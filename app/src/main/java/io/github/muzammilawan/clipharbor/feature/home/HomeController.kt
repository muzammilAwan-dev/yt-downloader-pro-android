package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.AudioChoice
import io.github.muzammilawan.clipharbor.core.AudioFormats
import io.github.muzammilawan.clipharbor.core.AudioPlan
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.QualityChoice
import io.github.muzammilawan.clipharbor.core.TaskStage
import io.github.muzammilawan.clipharbor.core.VideoQualities
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.core.VideoQualityPlan
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.download.TaskMediaMode
import io.github.muzammilawan.clipharbor.download.TaskRepository
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomePhase { INITIALIZING, READY, RESOLVING, CHOOSING, DOWNLOADING, PUBLISHING, PAUSING, PAUSED, DONE, FAILED, CANCELED }

data class HomeState(
    val phase: HomePhase = HomePhase.INITIALIZING,
    val engineReady: Boolean = false,
    val url: String = "",
    val inputError: String? = null,
    val title: String? = null,
    val qualities: List<QualityChoice> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val audioChoices: List<AudioChoice> = emptyList(),
    val selectedAudioFormatId: String? = null,
    val progress: Int? = null,
    val status: String = "Preparing the bundled engine…",
    val saved: PublishedMedia? = null,
    val pendingUrl: String? = null,
    val pauseReason: ErrorCode? = null,
) {
    val canDownload: Boolean get() = phase == HomePhase.CHOOSING && engineReady &&
        ((selectedAudioFormatId == null && qualities.any { it.available && it.quality == selectedQuality }) ||
            (selectedQuality == null && audioChoices.any { it.formatId == selectedAudioFormatId }))
    val downloadLabel: String get() = if (selectedAudioFormatId != null) "Download audio only" else "Download video + audio"
    val busy: Boolean get() = phase in setOf(HomePhase.INITIALIZING, HomePhase.RESOLVING, HomePhase.DOWNLOADING,
        HomePhase.PUBLISHING, HomePhase.PAUSING)
    override fun toString(): String = "HomeState(phase=$phase)"
}

data class PublishedMedia(val uri: String, val name: String, val location: String, val mimeType: String)

interface HomeObserver {
    fun event(task: String?, stage: String, event: String, fields: Map<String, String> = emptyMap()) {}
    fun failure(task: String?, error: DownloadError) {}
    fun line(task: String, line: String) {}
    fun line(task: String, stage: String, line: String) { line(task, line) }
}

/**
 * One-task entry flow; the caller owns its scope. When [repository] is supplied, every stage transition during
 * download is persisted to the durable queue. When [freeSpaceMb] returns a non-null value below the mode-specific
 * threshold, download is blocked before any engine or foreground work begins; the snapshot survives so the user
 * can free space and retry with one tap. Both features are opt-in so existing JVM tests pass unchanged.
 */
class HomeController(
    private val engine: Engine,
    private val scope: CoroutineScope,
    private val stagingRoot: File,
    private val publish: suspend (File) -> PublishedMedia,
    private val observer: HomeObserver = object : HomeObserver {},
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val newTaskId: () -> String = { UUID.randomUUID().toString() },
    private val foreground: DownloadForeground = DownloadForeground.ActivityOnly,
    private val publishAudio: suspend (File, String) -> PublishedMedia = { _, _ -> error("Audio publisher is not configured") },
    private val repository: TaskRepository? = null,
    private val freeSpaceMb: () -> Long? = { null },
    private val minFreeMbForVideo: Long = 1024L,
    private val minFreeMbForAudio: Long = 256L,
) {
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var prepared: Prepared? = null
    @Volatile private var activeTask: String? = null
    @Volatile private var timeoutTask: String? = null
    @Volatile private var closed = false

    private class Prepared(val task: String, val url: String, val snapshot: ResolvedSnapshot?, val createdAt: Long,
        val plans: Map<VideoQuality, VideoQualityPlan>, val audioPlans: Map<String, AudioPlan>)

    fun initialize() {
        if (closed || job?.isActive == true || state.value.engineReady) return
        mutable.update { it.copy(phase = HomePhase.INITIALIZING, status = "Preparing the bundled engine…") }
        job = scope.launch {
            val started = clockMs()
            try {
                engine.init().getOrThrow()
                observer.event(null, "APP", "engine_init", mapOf("ms" to (clockMs() - started).coerceAtLeast(0).toString()))
                mutable.update { it.copy(phase = HomePhase.READY, engineReady = true, status = "Ready when you are.") }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                fail(null, error, Stage.ANY)
            }
        }
    }

    fun editUrl(text: String) {
        if (closed || state.value.busy) return
        discardPrepared()
        mutable.update { it.copy(url = text.take(MediaEntry.MAX_INPUT_CHARS + 1), inputError = null,
            title = null, qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null, saved = null,
            phase = HomePhase.READY, pauseReason = null, status = "Review the link, then find formats.") }
    }

    fun receiveShared(text: String) {
        if (closed) return
        when (val input = MediaEntry.shared(text)) {
            is MediaEntry.Input.Invalid -> mutable.update { it.copy(inputError = input.message) }
            is MediaEntry.Input.Link -> {
                if (state.value.busy && state.value.phase != HomePhase.INITIALIZING) {
                    mutable.update { it.copy(pendingUrl = input.url) }
                } else {
                    discardPrepared()
                    mutable.update { it.copy(url = input.url, inputError = null, title = null,
                        qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null, saved = null, pauseReason = null,
                        phase = if (it.engineReady) HomePhase.READY else it.phase,
                        status = "Shared link ready. Review it before finding formats.") }
                }
            }
        }
    }

    fun usePendingLink() {
        if (state.value.busy) return
        val url = state.value.pendingUrl ?: return
        mutable.update { it.copy(pendingUrl = null) }
        editUrl(url)
    }

    fun resolve() {
        if (closed || state.value.busy || !state.value.engineReady) return
        val input = MediaEntry.pasted(state.value.url)
        if (input is MediaEntry.Input.Invalid) {
            mutable.update { it.copy(inputError = input.message) }
            return
        }
        val url = (input as MediaEntry.Input.Link).url
        discardPrepared()
        val task = newTaskId()
        activeTask = task
        mutable.update { it.copy(phase = HomePhase.RESOLVING, url = url, inputError = null,
            saved = null, title = null, qualities = emptyList(), selectedQuality = null,
            audioChoices = emptyList(), selectedAudioFormatId = null, progress = null, pauseReason = null,
            status = "Finding available video and audio formats…") }
        job = scope.launch {
            var snapshot: ResolvedSnapshot? = null
            val started = clockMs()
            try {
                observer.event(task, Stage.RESOLVING.name, "start")
                val outcome = engine.resolveForTask(url, task).getOrThrow()
                snapshot = outcome.resolvedSnapshot
                currentCoroutineContext().ensureActive()
                outcome.debugTail.takeLast(200).forEach { observer.line(task, Stage.RESOLVING.name, it) }
                val qualities = VideoQualities.choices(outcome.media.formats)
                val plans = qualities.filter { it.available }.associate { choice ->
                    choice.quality to checkNotNull(VideoQualities.plan(outcome.media.formats, choice.quality))
                }
                val audioChoices = AudioFormats.choices(outcome.media.formats)
                val audioPlans = audioChoices.associate { choice ->
                    choice.formatId to checkNotNull(AudioFormats.plan(outcome.media.formats, choice.formatId))
                }
                if (plans.isEmpty() && audioPlans.isEmpty()) throw EngineFailure(DownloadError(ErrorCode.FORMATS_WITHHELD, Stage.RESOLVING))
                prepared = Prepared(task, url, snapshot, clockMs(), plans, audioPlans)
                snapshot = null // Ownership is transferred to the chooser, not persisted in UI/history.
                observer.event(task, Stage.RESOLVING.name, "ok", mapOf("formats" to outcome.media.formats.size.toString(),
                    "ms" to (clockMs() - started).coerceAtLeast(0).toString(),
                    "reportedMaxResolution" to (outcome.media.formats.filter { it.hasVideo }.mapNotNull(VideoQualities::resolution).maxOrNull()?.toString() ?: "unknown"),
                    "visibleResolutions" to VideoQualities.visibleChoices(qualities).map { minOf(it.width!!, it.height!!) }.joinToString(",").ifEmpty { "none" },
                    "audioChoices" to audioChoices.size.toString()))
                mutable.update { it.copy(phase = HomePhase.CHOOSING, title = outcome.media.title,
                    qualities = qualities, selectedQuality = VideoQualities.default(qualities),
                    audioChoices = audioChoices, selectedAudioFormatId = if (plans.isEmpty()) audioChoices.first().formatId else null,
                    status = "Choose video + audio or an original audio-only stream. Then confirm Download.") }
            } catch (canceled: CancellationException) {
                canceled(task)
                throw canceled
            } catch (error: Exception) {
                fail(task, error, Stage.RESOLVING)
            } finally {
                snapshot?.close()
                activeTask = null
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        if (!closed && state.value.phase == HomePhase.CHOOSING &&
            state.value.qualities.any { it.quality == quality && it.available }) {
            mutable.update { it.copy(selectedQuality = quality, selectedAudioFormatId = null) }
            observer.event(prepared?.task, Stage.RESOLVING.name, "select_video", mapOf("requestedResolution" to quality.pixels.toString()))
        }
    }

    fun selectAudio(formatId: String) {
        if (!closed && state.value.phase == HomePhase.CHOOSING && prepared?.audioPlans?.containsKey(formatId) == true) {
            mutable.update { it.copy(selectedQuality = null, selectedAudioFormatId = formatId) }
            observer.event(prepared?.task, Stage.RESOLVING.name, "select_audio", mapOf("formatId" to formatId, "mediaMode" to "audio"))
        }
    }

    fun download() {
        if (closed || !state.value.canDownload) return
        val input = prepared ?: return
        val plan = state.value.selectedQuality?.let(input.plans::get)
        val audio = state.value.selectedAudioFormatId?.let(input.audioPlans::get)
        if ((plan == null) == (audio == null)) return
        val mode = if (audio != null) "audio" else "video"
        if (clockMs() - input.createdAt !in 0..ResolvedSnapshot.MAX_AGE_MS) {
            discardPrepared()
            resolve()
            return // Refresh requires another explicit download confirmation.
        }
        // Pre-flight free-space gate. Warning state, not a hard failure (docs/02 §2): phase stays CHOOSING,
        // prepared snapshot preserved, one tap after freeing space retries without re-resolving.
        val minFreeMb = if (audio != null) minFreeMbForAudio else minFreeMbForVideo
        val availableMb = freeSpaceMb()
        if (availableMb != null && availableMb < minFreeMb) {
            observer.event(input.task, Stage.DOWNLOADING.name, "insufficient_space",
                mapOf("availableMb" to availableMb.toString(), "requiredMb" to minFreeMb.toString()))
            mutable.update { it.copy(status = "Not enough free storage: $availableMb MB available, $minFreeMb MB required. Free some space, then tap Download again.") }
            return
        }
        prepared = null
        activeTask = input.task
        mutable.update { it.copy(phase = HomePhase.DOWNLOADING, progress = null,
            status = if (audio != null) "Downloading original audio…" else "Downloading video and audio…") }
        // Enter the cleanup block before returning to the caller, even if the owner scope is canceled.
        // Android admission starts from this explicit user action; engine work still dispatches to IO.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val directory = File(stagingRoot, input.task)
            var lease: DownloadForeground.Lease? = null
            var dbId: Long? = null
            try {
                currentCoroutineContext().ensureActive()
                val admitted = foreground.acquire(input.task)
                lease = admitted
                currentCoroutineContext().ensureActive()
                // Persist a durable row before engine work begins. The row starts at QUEUED (repository contract),
                // then advances through RESOLVING and DOWNLOADING so a mid-download process death is visible in the
                // queue table. runCatching keeps a DB hiccup from cancelling the actual download; an orphaned
                // QUEUED row is tolerated until the queue-UI batch adds a cleanup pass.
                dbId = if (repository == null) null else runCatching {
                    val id = repository.create(
                        url = input.url,
                        mediaMode = if (audio != null) TaskMediaMode.AUDIO else TaskMediaMode.VIDEO,
                        requestedResolution = plan?.quality?.pixels ?: 0,
                        audioFormatId = audio?.formatId,
                        mergeContainer = if ((plan?.quality?.pixels ?: 0) >= 1440) "mkv" else "mp4/mkv",
                    )
                    runCatching { repository.advance(id, TaskStage.RESOLVING) }
                    runCatching { repository.advance(id, TaskStage.DOWNLOADING) }
                    id
                }.onFailure {
                    observer.event(input.task, Stage.DOWNLOADING.name, "db_create_failed",
                        mapOf("err" to it.javaClass.simpleName))
                }.getOrNull()
                observer.event(input.task, Stage.DOWNLOADING.name, "start", mapOf(
                    "mediaMode" to mode, "requestedResolution" to (plan?.quality?.pixels?.toString() ?: "none"), "qualityAxis" to "shortEdge",
                    "resolveReuse" to (input.snapshot != null).toString(),
                    "concurrentFragments" to "4"))
                val lastLoggedProgress = AtomicInteger(-1)
                val outcome = engine.download(DownloadRequest(input.url, plan?.quality?.pixels ?: 1080, directory, input.task,
                    mergeContainer = if ((plan?.quality?.pixels ?: 0) >= 1440) "mkv" else "mp4/mkv",
                    resolvedSnapshot = input.snapshot, qualityPlan = plan, audioPlan = audio), onProgress = { event ->
                    event.percent?.takeIf { it.isFinite() && it in 0f..100f }?.toInt()?.let { percent ->
                        if (!closed && activeTask == input.task && state.value.phase == HomePhase.DOWNLOADING) {
                            mutable.update {
                                if (!closed && activeTask == input.task && it.phase == HomePhase.DOWNLOADING) it.copy(progress = percent) else it
                            }
                            if (state.value.phase == HomePhase.DOWNLOADING && lastLoggedProgress.getAndSet(percent) != percent) {
                                observer.event(input.task, Stage.DOWNLOADING.name, "progress", mapOf("percent" to percent.toString()))
                            }
                            admitted.progress(percent)
                        }
                    }
                }, onLine = {
                    if (!closed && activeTask == input.task && timeoutTask != input.task) observer.line(input.task, Stage.DOWNLOADING.name, it)
                }).getOrThrow()
                currentCoroutineContext().ensureActive()
                observer.event(input.task, Stage.DOWNLOADING.name, "engine_result", mapOf(
                    "exit" to outcome.exitCode.toString(), "ms" to outcome.elapsedMs.toString()))
                val file = outcome.files.singleOrNull()?.takeIf { it.isFile && it.length() > 0 }
                    ?: throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING))
                if (audio != null && (file.extension.lowercase(Locale.ROOT) != audio.extension ||
                    outcome.selectedFormatIds != listOf(audio.formatId) || outcome.selectedWidth != null || outcome.selectedHeight != null)) {
                    throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                        detail = "Original audio result does not match the selected plan"))
                }
                observer.event(input.task, Stage.DOWNLOADING.name, "selected_formats", mapOf(
                    "ids" to outcome.selectedFormatIds.joinToString("+").ifEmpty { "unknown" },
                    "actualHeight" to (outcome.selectedHeight?.toString() ?: "unknown"),
                    "actualWidth" to (outcome.selectedWidth?.toString() ?: "unknown"),
                    "actualResolution" to (if (outcome.selectedWidth != null && outcome.selectedHeight != null)
                        minOf(outcome.selectedWidth, outcome.selectedHeight).toString() else "unknown"),
                    "mediaMode" to mode, "requestedResolution" to (plan?.quality?.pixels?.toString() ?: "none"), "qualityAxis" to "shortEdge",
                    "reusedResolve" to outcome.reusedResolve.toString()))
                mutable.update { it.copy(phase = HomePhase.PUBLISHING, progress = null, status = "Saving the finished file…") }
                admitted.publishing()
                dbId?.let { id -> runCatching { repository?.advance(id, TaskStage.PUBLISHING) } }
                val publicationStarted = clockMs()
                observer.event(input.task, Stage.PUBLISHING.name, "start")
                currentCoroutineContext().ensureActive()
                val saved = if (audio != null) publishAudio(file, audio.mimeType) else publish(file)
                dbId?.let { id -> runCatching { repository?.publish(id, saved.uri) } }
                observer.event(input.task, Stage.PUBLISHING.name, "ok", mapOf("uri" to saved.uri,
                    "bytes" to file.length().toString(), "mime" to saved.mimeType,
                    "ms" to (clockMs() - publicationStarted).coerceAtLeast(0).toString()))
                val width = outcome.selectedWidth
                val height = outcome.selectedHeight
                val actual = if (width != null && height != null) "$width × $height (${minOf(width, height)}p short edge)"
                    else "dimensions not fully reported"
                val ids = outcome.selectedFormatIds.joinToString("+").ifEmpty { "unreported" }
                mutable.update { it.copy(phase = HomePhase.DONE, saved = saved,
                    status = if (audio != null) "Saved. Original audio · ${audio.extension.uppercase(Locale.ROOT)} · format $ids."
                        else "Saved. Selected video: $actual · formats $ids.") }
                // Success only; cancellation does not prove native descendants have released staging files.
                directory.deleteRecursively()
            } catch (canceled: CancellationException) {
                canceled(input.task)
                dbId?.let { id ->
                    withContext(NonCancellable) {
                        if (timeoutTask == input.task) runCatching { repository?.pause(id) }
                        else runCatching { repository?.cancel(id) }
                    }
                }
                throw canceled
            } catch (error: Exception) {
                if (timeoutTask != input.task) {
                    fail(input.task, error, if (state.value.phase == HomePhase.PUBLISHING) Stage.PUBLISHING else Stage.DOWNLOADING)
                    val code = (error as? EngineFailure)?.error?.code?.name ?: ErrorCode.UNAVAILABLE.name
                    dbId?.let { id ->
                        withContext(NonCancellable) {
                            runCatching { repository?.fail(id, code) }
                        }
                    }
                }
            } finally {
                try {
                    lease?.close()
                } finally {
                    input.snapshot?.close()
                    activeTask = null
                    finishTimeout(input.task)
                }
            }
        }
    }

    /** Saving is a commit boundary, not an operation the Cancel button can safely undo. */
    fun cancel() {
        if (closed || state.value.phase !in setOf(HomePhase.RESOLVING, HomePhase.DOWNLOADING, HomePhase.CHOOSING)) return
        activeTask?.let(engine::cancel)
        job?.cancel()
        discardPrepared()
        if (!state.value.busy) mutable.update { it.copy(phase = HomePhase.CANCELED, audioChoices = emptyList(), selectedAudioFormatId = null,
            status = "Selection dismissed.") }
    }

    fun close() {
        if (closed) return
        closed = true
        try {
            if (activeTask != timeoutTask) activeTask?.let(engine::cancel)
        } finally {
            job?.cancel()
            discardPrepared()
        }
    }

    /**
     * Android-main-thread boundary for Service.onTimeout. Stop the service before any native cancel
     * attempt, even for stale/closed tasks. Never join a worker here: its finally path owns cleanup.
     * Unlike user Cancel, the platform may interrupt publication; do not report an uncommitted save.
     */
    fun onForegroundTimeout(taskId: String, stopService: () -> Unit): Boolean {
        var interrupted = false
        try { stopService() } finally { interrupted = pauseTimedOutTask(taskId) }
        return interrupted
    }

    private fun pauseTimedOutTask(task: String): Boolean {
        if (closed || activeTask != task || state.value.phase !in setOf(HomePhase.DOWNLOADING, HomePhase.PUBLISHING)) return false
        timeoutTask = task
        mutable.update { it.copy(phase = HomePhase.PAUSING, pauseReason = ErrorCode.FGS_TIMEOUT, progress = null,
            status = "Android stopped background execution. Waiting for task cleanup…") }
        try { engine.cancel(task) } finally { job?.cancel() }
        return true
    }

    private fun finishTimeout(task: String) {
        if (timeoutTask != task) return
        timeoutTask = null
        if (closed) return
        mutable.update { it.copy(phase = HomePhase.PAUSED, pauseReason = ErrorCode.FGS_TIMEOUT, progress = null,
            qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null, saved = null,
            status = "Paused: Android's background time limit was reached. Automatic rescheduling is not available yet. Find formats again to start a new attempt.") }
        observer.event(task, Stage.DOWNLOADING.name, "paused", mapOf(
            "code" to ErrorCode.FGS_TIMEOUT.name, "recovery" to "PAUSE_AND_RESCHEDULE", "rescheduled" to "false"))
    }

    private fun discardPrepared() {
        prepared?.snapshot?.close()
        prepared = null
    }

    private fun canceled(task: String) {
        if (!closed && timeoutTask != task) {
            observer.event(task, "ANY", "canceled")
            mutable.update { it.copy(phase = HomePhase.CANCELED, progress = null, audioChoices = emptyList(), selectedAudioFormatId = null,
                status = "Canceled. Native process cleanup is best-effort.") }
        }
    }

    private fun fail(task: String?, throwable: Throwable, stage: Stage) {
        if (closed) return
        val error = (throwable as? EngineFailure)?.error ?: DownloadError(ErrorCode.UNAVAILABLE, stage, detail = throwable.message)
        observer.failure(task, error)
        discardPrepared()
        val message = when (error.code) {
            ErrorCode.AGE_OR_BOT_GATE, ErrorCode.LOGIN_REQUIRED -> "The site requires verification or sign-in. In-app login is not available yet."
            ErrorCode.FORMATS_WITHHELD -> "No selectable video + audio qualities or original audio streams were reported."
            ErrorCode.RESOLVE_TIMEOUT, ErrorCode.HTTP_403_ON_MEDIA -> "The link or media access may have expired. Find formats again."
            ErrorCode.NO_SPACE -> "Not enough storage. Free some space, then find formats again."
            else -> "This attempt failed (${error.code.name}). Diagnostics has the details."
        }
        mutable.update { it.copy(phase = HomePhase.FAILED, progress = null, audioChoices = emptyList(), selectedAudioFormatId = null, status = message) }
    }
}
