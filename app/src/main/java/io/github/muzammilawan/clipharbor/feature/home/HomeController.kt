package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.AudioChoice
import io.github.muzammilawan.clipharbor.core.AudioFormats
import io.github.muzammilawan.clipharbor.core.AudioPlan
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ErrorRecovery
import io.github.muzammilawan.clipharbor.core.ExactVideoChoice
import io.github.muzammilawan.clipharbor.core.ExactVideoFormats
import io.github.muzammilawan.clipharbor.core.ExactVideoPlan
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.QualityChoice
import io.github.muzammilawan.clipharbor.core.VideoQualities
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.core.VideoQualityPlan
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.core.StorageBudget
import io.github.muzammilawan.clipharbor.core.StorageSpace
import io.github.muzammilawan.clipharbor.core.StorageReport
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.download.TaskPhase
import io.github.muzammilawan.clipharbor.download.TaskRecord
import io.github.muzammilawan.clipharbor.download.TaskRepository
import io.github.muzammilawan.clipharbor.download.TaskSelection
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomePhase { INITIALIZING, STAGING, READY, RESOLVING, CHOOSING, DOWNLOADING, PUBLISHING, PAUSING, PAUSED, DONE, FAILED, CANCELED }

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
    val exactVideoChoices: List<ExactVideoChoice> = emptyList(),
    val selectedExactVideoId: String? = null,
    val progress: Int? = null,
    val status: String = "Preparing the bundled engine…",
    val saved: PublishedMedia? = null,
    val pendingUrl: String? = null,
    val pauseReason: ErrorCode? = null,
    val storageReport: StorageReport? = null,
    val failureCode: ErrorCode? = null,
    val publicationReviewRequired: Boolean = false,
) {
    val canDownload: Boolean get() = phase == HomePhase.CHOOSING && engineReady && !publicationReviewRequired &&
        listOf(selectedQuality, selectedAudioFormatId, selectedExactVideoId).count { it != null } == 1 &&
        (qualities.any { it.available && it.quality == selectedQuality } || audioChoices.any { it.formatId == selectedAudioFormatId } ||
            exactVideoChoices.any { it.formatId == selectedExactVideoId })
    val downloadLabel: String get() = if (selectedAudioFormatId != null) "Download audio only" else "Download video + audio"
    val busy: Boolean get() = phase in setOf(HomePhase.INITIALIZING, HomePhase.STAGING, HomePhase.RESOLVING, HomePhase.DOWNLOADING,
        HomePhase.PUBLISHING, HomePhase.PAUSING)
    // Do not accidentally expose source/signed URLs or media titles when debugging an object.
    override fun toString(): String = "HomeState(phase=$phase)"
}

data class PublishedMedia(val uri: String, val name: String, val location: String, val mimeType: String)

interface HomeObserver {
    fun event(task: String?, stage: String, event: String, fields: Map<String, String> = emptyMap()) {}
    fun failure(task: String?, error: DownloadError) {}
    fun line(task: String, line: String) {}
    fun line(task: String, stage: String, line: String) { line(task, line) }
}

/** One-task entry flow; the caller owns its scope. Durable queue/foreground execution is separate. */
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
    private val queue: TaskRepository? = null,
    private val storageSpace: (suspend () -> StorageSpace)? = null,
) {
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    val tasks = queue?.rows ?: flowOf(emptyList())
    val hasDurableQueue: Boolean get() = queue != null
    private var job: Job? = null
    private var prepared: Prepared? = null
    private var currentRecord: String? = null
    private var reviewedRecord: TaskRecord? = null
    private var retryAfterMs = 0L
    @Volatile private var activeTask: String? = null
    @Volatile private var timeoutTask: String? = null
    @Volatile private var closed = false

    private class Prepared(val task: String, val url: String, val snapshot: ResolvedSnapshot?, val createdAt: Long,
        val plans: Map<VideoQuality, VideoQualityPlan>, val audioPlans: Map<String, AudioPlan>, val recordId: String?,
        val videoEstimates: Map<VideoQuality, Long?>, val audioEstimates: Map<String, Long?>, val exactPlans: Map<String, ExactVideoPlan>,
        val chooserRevision: Long?)

    fun initialize() {
        if (closed || job?.isActive == true || state.value.engineReady) return
        mutable.update { it.copy(phase = HomePhase.INITIALIZING, status = "Preparing the bundled engine…") }
        job = scope.launch {
            val started = clockMs()
            try {
                queue?.recoverInterrupted()
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
        if (state.value.publicationReviewRequired && text.trim() == state.value.url) return
        if (text == state.value.url && state.value.phase == HomePhase.CHOOSING) return
        abandonChooser()
        currentRecord = null
        reviewedRecord = null
        discardPrepared()
        mutable.update { it.copy(url = text.take(MediaEntry.MAX_INPUT_CHARS + 1), inputError = null,
            title = null, qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null,
            exactVideoChoices = emptyList(), selectedExactVideoId = null, saved = null,
            phase = HomePhase.READY, pauseReason = null, storageReport = null, failureCode = null, publicationReviewRequired = false,
            status = "Review the link, then find formats.") }
    }

    fun receiveShared(text: String) {
        if (closed) return
        when (val input = MediaEntry.shared(text)) {
            is MediaEntry.Input.Invalid -> mutable.update { it.copy(inputError = input.message) }
            is MediaEntry.Input.Link -> {
                if (state.value.publicationReviewRequired && input.url == state.value.url) return
                if (input.url == state.value.url && state.value.phase == HomePhase.CHOOSING && reusable(input.url)) return
                if (state.value.busy && state.value.phase != HomePhase.INITIALIZING) {
                    mutable.update { it.copy(pendingUrl = input.url) }
                } else {
                    abandonChooser()
                    currentRecord = null
                    reviewedRecord = null
                    discardPrepared()
                    mutable.update { it.copy(url = input.url, inputError = null, title = null,
                        qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null,
                        exactVideoChoices = emptyList(), selectedExactVideoId = null, saved = null, pauseReason = null, failureCode = null,
                        storageReport = null, publicationReviewRequired = false,
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

    fun resolve(forceRefresh: Boolean = false) {
        if (closed || state.value.busy || job?.isActive == true || !state.value.engineReady) return
        if (state.value.publicationReviewRequired) {
            mutable.update { it.copy(status = "Check Downloads for an existing file, then use the queue's explicit interrupted-save review.") }; return
        }
        state.value.failureCode?.let { code ->
            val recovery = ErrorRecovery.forCode(code)
            if (!recovery.freshResolve || clockMs() < retryAfterMs) {
                mutable.update { it.copy(status = recovery.explanation) }; return
            }
        }
        val input = MediaEntry.pasted(state.value.url)
        if (input is MediaEntry.Input.Invalid) {
            mutable.update { it.copy(inputError = input.message) }
            return
        }
        val url = (input as MediaEntry.Input.Link).url
        if (!forceRefresh && reusable(url)) {
            observer.event(prepared?.task, Stage.RESOLVING.name, "chooser_reuse", mapOf("ms" to "0", "network" to "false"))
            mutable.update { it.copy(status = "Using this task's recently resolved formats. Refresh explicitly for a new site check.") }
            return
        }
        val existingRecord = currentRecord.takeIf { state.value.phase in setOf(HomePhase.CHOOSING, HomePhase.FAILED, HomePhase.PAUSED, HomePhase.CANCELED, HomePhase.READY) }
        val requestedSelection = reviewedRecord?.selection
        reviewedRecord = null
        discardPrepared()
        val task = newTaskId()
        activeTask = task
        mutable.update { it.copy(phase = HomePhase.RESOLVING, url = url, inputError = null,
            saved = null, title = null, qualities = emptyList(), selectedQuality = null,
            audioChoices = emptyList(), selectedAudioFormatId = null, exactVideoChoices = emptyList(), selectedExactVideoId = null,
            progress = null, pauseReason = null, storageReport = null, failureCode = null,
            status = "Finding available video and audio formats…") }
        job = scope.launch {
            var snapshot: ResolvedSnapshot? = null
            var recordId: String? = existingRecord
            val started = clockMs()
            try {
                if (queue != null) {
                    if (recordId == null) recordId = queue.stage(task, url).id
                    currentRecord = recordId
                    queue.transition(recordId, TaskPhase.RESOLVING)
                }
                observer.event(task, Stage.RESOLVING.name, "start")
                val outcome = engine.resolveForTask(url, task).getOrThrow()
                snapshot = outcome.resolvedSnapshot
                currentCoroutineContext().ensureActive()
                val timings = outcome.timingFields.filter { it.key in setOf("initMs", "subprocessMs", "metadataReplayMs") && it.value >= 0 }
                if (timings.isNotEmpty()) observer.event(task, Stage.RESOLVING.name, "timings", timings.mapValues { it.value.toString() })
                outcome.debugTail.takeLast(200).forEach { observer.line(task, Stage.RESOLVING.name, it) }
                val qualities = VideoQualities.choices(outcome.media.formats)
                val plans = qualities.filter { it.available }.associate { choice ->
                    choice.quality to checkNotNull(VideoQualities.plan(outcome.media.formats, choice.quality))
                }
                val audioChoices = AudioFormats.choices(outcome.media.formats)
                val audioPlans = audioChoices.associate { choice ->
                    choice.formatId to checkNotNull(AudioFormats.plan(outcome.media.formats, choice.formatId))
                }
                val exactChoices = ExactVideoFormats.choices(outcome.media.formats)
                val exactPlans = exactChoices.associate { it.formatId to checkNotNull(ExactVideoFormats.plan(outcome.media.formats, it.formatId)) }
                if (plans.isEmpty() && audioPlans.isEmpty()) throw EngineFailure(DownloadError(ErrorCode.FORMATS_WITHHELD, Stage.RESOLVING))
                val defaultVideo = requestedSelection?.videoHeight?.let { pixels -> plans.keys.firstOrNull { it.pixels == pixels } }
                    ?: VideoQualities.default(qualities)
                val defaultAudio = requestedSelection?.audioFormatId?.takeIf { it in audioPlans }
                    ?: if (plans.isEmpty()) audioChoices.first().formatId else null
                val defaultExact = requestedSelection?.videoFormatId?.takeIf { it in exactPlans }
                val selection = if (defaultExact != null) TaskSelection(videoFormatId = defaultExact)
                    else if (defaultAudio != null) TaskSelection(audioFormatId = defaultAudio)
                    else TaskSelection(videoHeight = defaultVideo?.pixels)
                val chooser = persistPhase(recordId, TaskPhase.CHOOSING, selection = selection, title = outcome.media.title)
                prepared = Prepared(task, url, snapshot, clockMs(), plans, audioPlans, recordId,
                    plans.mapValues { StorageBudget.estimate(outcome.media.formats, it.value) },
                    audioChoices.associate { it.formatId to it.approxBytes }, exactPlans, chooser?.revision)
                snapshot = null // Ownership is transferred to the chooser, not persisted in UI/history.
                observer.event(task, Stage.RESOLVING.name, "ok", mapOf("formats" to outcome.media.formats.size.toString(),
                    "ms" to (clockMs() - started).coerceAtLeast(0).toString(),
                    "reportedMaxResolution" to (outcome.media.formats.filter { it.hasVideo }.mapNotNull(VideoQualities::resolution).maxOrNull()?.toString() ?: "unknown"),
                    "visibleResolutions" to VideoQualities.visibleChoices(qualities).map { minOf(it.width!!, it.height!!) }.joinToString(",").ifEmpty { "none" },
                    "audioChoices" to audioChoices.size.toString()))
                mutable.update { it.copy(phase = HomePhase.CHOOSING, title = outcome.media.title,
                    qualities = qualities, selectedQuality = if (defaultAudio == null && defaultExact == null) defaultVideo else null,
                    audioChoices = audioChoices, selectedAudioFormatId = if (defaultExact == null) defaultAudio else null,
                    exactVideoChoices = exactChoices, selectedExactVideoId = defaultExact,
                    status = "Choose video + audio or an original audio-only stream. Then confirm Download.") }
            } catch (canceled: CancellationException) {
                canceled(task, recordId)
                throw canceled
            } catch (error: Exception) {
                fail(task, error, Stage.RESOLVING, recordId)
            } finally {
                snapshot?.close()
                activeTask = null
            }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        if (!closed && state.value.phase == HomePhase.CHOOSING &&
            state.value.qualities.any { it.quality == quality && it.available }) {
            mutable.update { it.copy(selectedQuality = quality, selectedAudioFormatId = null, selectedExactVideoId = null) }
            observer.event(prepared?.task, Stage.RESOLVING.name, "select_video", mapOf("requestedResolution" to quality.pixels.toString()))
        }
    }

    fun selectAudio(formatId: String) {
        if (!closed && state.value.phase == HomePhase.CHOOSING && prepared?.audioPlans?.containsKey(formatId) == true) {
            mutable.update { it.copy(selectedQuality = null, selectedAudioFormatId = formatId, selectedExactVideoId = null) }
            observer.event(prepared?.task, Stage.RESOLVING.name, "select_audio", mapOf("formatId" to formatId, "mediaMode" to "audio"))
        }
    }

    fun selectExactVideo(formatId: String) {
        if (!closed && state.value.phase == HomePhase.CHOOSING && prepared?.exactPlans?.containsKey(formatId) == true) {
            mutable.update { it.copy(selectedQuality = null, selectedAudioFormatId = null, selectedExactVideoId = formatId) }
            observer.event(prepared?.task, Stage.RESOLVING.name, "select_exact_video", mapOf("formatId" to formatId))
        }
    }

    fun download() {
        if (closed || job?.isActive == true || !state.value.canDownload) return
        val input = prepared ?: return
        val plan = state.value.selectedQuality?.let(input.plans::get)
        val audio = state.value.selectedAudioFormatId?.let(input.audioPlans::get)
        val exact = state.value.selectedExactVideoId?.let(input.exactPlans::get)
        if (listOf(plan, audio, exact).count { it != null } != 1) return
        val mode = if (audio != null) "audio" else "video"
        if (clockMs() - input.createdAt !in 0..ResolvedSnapshot.MAX_AGE_MS ||
            input.snapshot?.availableFor(input.url, input.task) == false) {
            discardPrepared()
            resolve()
            return // Refresh requires another explicit download confirmation.
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
            try {
                currentCoroutineContext().ensureActive()
                storageSpace?.let { probe ->
                    checkStorage(StorageBudget.download(probe(), if (audio != null) input.audioEstimates[audio.formatId]
                        else exact?.estimatedBytes ?: input.videoEstimates[plan?.quality], merging = audio == null), input.task, Stage.DOWNLOADING)
                }
                persistPhase(input.recordId, TaskPhase.DOWNLOADING, selection = if (audio != null)
                    TaskSelection(audioFormatId = audio.formatId) else if (exact != null) TaskSelection(videoFormatId = exact.choice.formatId)
                    else TaskSelection(videoHeight = plan?.quality?.pixels))
                val admitted = foreground.acquire(input.task)
                lease = admitted
                currentCoroutineContext().ensureActive()
                observer.event(input.task, Stage.DOWNLOADING.name, "start", mapOf(
                    "mediaMode" to mode, "requestedResolution" to (plan?.quality?.pixels?.toString() ?: "none"), "qualityAxis" to "shortEdge",
                    "resolveReuse" to (input.snapshot != null).toString(),
                    "concurrentFragments" to "4"))
                val lastLoggedProgress = AtomicInteger(-1)
                val outcome = engine.download(DownloadRequest(input.url, plan?.quality?.pixels ?: 1080, directory, input.task,
                    mergeContainer = if ((plan?.quality?.pixels ?: 0) >= 1440) "mkv" else "mp4/mkv",
                    resolvedSnapshot = input.snapshot, qualityPlan = plan, audioPlan = audio, exactVideoPlan = exact), onProgress = { event ->
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
                if (exact != null && !ExactVideoFormats.matches(exact, outcome.selectedFormatIds, outcome.selectedWidth, outcome.selectedHeight)) {
                    throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                        detail = "Exact video result does not match the selected plan"))
                }
                observer.event(input.task, Stage.DOWNLOADING.name, "selected_formats", mapOf(
                    "ids" to outcome.selectedFormatIds.joinToString("+").ifEmpty { "unknown" },
                    "actualHeight" to (outcome.selectedHeight?.toString() ?: "unknown"),
                    "actualWidth" to (outcome.selectedWidth?.toString() ?: "unknown"),
                    "actualResolution" to (if (outcome.selectedWidth != null && outcome.selectedHeight != null)
                        minOf(outcome.selectedWidth, outcome.selectedHeight).toString() else "unknown"),
                    "mediaMode" to mode, "requestedResolution" to (plan?.quality?.pixels?.toString() ?: "none"), "qualityAxis" to "shortEdge",
                    "reusedResolve" to outcome.reusedResolve.toString()))
                storageSpace?.let { probe -> checkStorage(StorageBudget.publication(probe(), file.length()), input.task, Stage.PUBLISHING) }
                persistPhase(input.recordId, TaskPhase.PUBLISHING)
                mutable.update { it.copy(phase = HomePhase.PUBLISHING, progress = null, publicationReviewRequired = true,
                    status = "Saving the finished file…") }
                admitted.publishing()
                val publicationStarted = clockMs()
                observer.event(input.task, Stage.PUBLISHING.name, "start")
                currentCoroutineContext().ensureActive()
                val saved = if (audio != null) publishAudio(file, audio.mimeType) else publish(file)
                mutable.update { it.copy(saved = saved, status = "File published; recording the result…") }
                persistPhase(input.recordId, TaskPhase.DONE, saved = saved)
                observer.event(input.task, Stage.PUBLISHING.name, "ok", mapOf("uri" to saved.uri,
                    "bytes" to file.length().toString(), "mime" to saved.mimeType,
                    "ms" to (clockMs() - publicationStarted).coerceAtLeast(0).toString()))
                val width = outcome.selectedWidth
                val height = outcome.selectedHeight
                val actual = if (width != null && height != null) "$width × $height (${minOf(width, height)}p short edge)"
                    else "dimensions not fully reported"
                val ids = outcome.selectedFormatIds.joinToString("+").ifEmpty { "unreported" }
                mutable.update { it.copy(phase = HomePhase.DONE, saved = saved, publicationReviewRequired = false,
                    status = if (audio != null) "Saved. Original audio · ${audio.extension.uppercase(Locale.ROOT)} · format $ids."
                        else "Saved. Selected video: $actual · formats $ids.") }
                // Success only; cancellation does not prove native descendants have released staging files.
                if (!runCatching { directory.deleteRecursively() }.getOrDefault(false)) observer.event(input.task, "CLEANUP", "staging_retained")
            } catch (canceled: CancellationException) {
                canceled(input.task, input.recordId)
                throw canceled
            } catch (error: Exception) {
                if (timeoutTask != input.task) {
                    fail(input.task, error, if (state.value.phase == HomePhase.PUBLISHING) Stage.PUBLISHING else Stage.DOWNLOADING, input.recordId)
                }
            } finally {
                try {
                    lease?.close()
                } finally {
                    input.snapshot?.close()
                    activeTask = null
                    finishTimeout(input.task, input.recordId)
                }
            }
        }
    }

    /** Saving is a commit boundary, not an operation the Cancel button can safely undo. */
    fun cancel() {
        if (closed || state.value.phase !in setOf(HomePhase.RESOLVING, HomePhase.DOWNLOADING, HomePhase.CHOOSING)) return
        activeTask?.let(engine::cancel)
        job?.cancel()
        if (state.value.phase == HomePhase.CHOOSING) abandonChooser()
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
            abandonChooser()
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

    private suspend fun finishTimeout(task: String, recordId: String?) {
        if (timeoutTask != task) return
        persistTerminal(recordId, TaskPhase.PAUSED,
            if (state.value.publicationReviewRequired) "PUBLICATION_INTERRUPTED" else ErrorCode.FGS_TIMEOUT.name)
        timeoutTask = null
        if (closed) return
        mutable.update { it.copy(phase = HomePhase.PAUSED, pauseReason = ErrorCode.FGS_TIMEOUT, progress = null,
            qualities = emptyList(), selectedQuality = null, audioChoices = emptyList(), selectedAudioFormatId = null,
            exactVideoChoices = emptyList(), selectedExactVideoId = null,
            saved = if (it.publicationReviewRequired) it.saved else null,
            status = if (it.publicationReviewRequired) "Paused while saving. Check Downloads first, then explicitly review the interrupted save in the queue."
                else "Paused: Android's background time limit was reached. Automatic rescheduling is not available yet. Find formats again to start a new attempt.") }
        observer.event(task, Stage.DOWNLOADING.name, "paused", mapOf(
            "code" to ErrorCode.FGS_TIMEOUT.name, "recovery" to "PAUSE_AND_RESCHEDULE", "rescheduled" to "false"))
    }

    private fun discardPrepared() {
        prepared?.snapshot?.close()
        prepared = null
    }

    private fun reusable(url: String): Boolean {
        val input = prepared ?: return false
        return state.value.phase == HomePhase.CHOOSING && input.url == url &&
            clockMs() - input.createdAt in 0..ResolvedSnapshot.MAX_AGE_MS && input.snapshot?.availableFor(url, input.task) == true
    }

    private suspend fun canceled(task: String, recordId: String? = null) {
        if (timeoutTask != task) persistTerminal(recordId,
            if (state.value.publicationReviewRequired) TaskPhase.PAUSED else TaskPhase.CANCELED,
            if (state.value.publicationReviewRequired) "PUBLICATION_INTERRUPTED" else ErrorCode.CANCELED.name)
        if (!closed && timeoutTask != task) {
            observer.event(task, "ANY", "canceled")
            mutable.update { it.copy(phase = if (it.publicationReviewRequired) HomePhase.PAUSED else HomePhase.CANCELED,
                progress = null, audioChoices = emptyList(), selectedAudioFormatId = null,
                status = if (it.publicationReviewRequired) "Saving was interrupted. Check Downloads first, then explicitly review the interrupted save in the queue."
                    else "Canceled. Native process cleanup is best-effort.") }
        }
    }

    private suspend fun fail(task: String?, throwable: Throwable, stage: Stage, recordId: String? = null) {
        val error = (throwable as? EngineFailure)?.error ?: DownloadError(ErrorCode.UNAVAILABLE, stage, detail = throwable.message)
        persistTerminal(recordId, if (state.value.publicationReviewRequired) TaskPhase.PAUSED else TaskPhase.FAILED,
            if (state.value.publicationReviewRequired) "PUBLICATION_INTERRUPTED" else error.code.name)
        if (closed) return
        observer.failure(task, error)
        discardPrepared()
        val recovery = ErrorRecovery.forCode(error.code)
        retryAfterMs = clockMs() + recovery.waitMs
        observer.event(task, stage.name, "recovery", mapOf("code" to error.code.name, "contractAction" to recovery.contractAction,
            "freshResolveAvailable" to recovery.freshResolve.toString(), "automatic" to "false"))
        mutable.update { it.copy(phase = HomePhase.FAILED, progress = null, audioChoices = emptyList(), selectedAudioFormatId = null,
            failureCode = error.code, status = recovery.explanation + if (it.publicationReviewRequired)
                " Saving may already have produced a file. Check Downloads before explicit interrupted-save review in the queue." else "") }
    }

    fun stageCurrentLink() {
        val repository = queue ?: return
        if (closed || state.value.busy || job?.isActive == true) return
        if (state.value.publicationReviewRequired) return
        val source = MediaEntry.pasted(state.value.url)
        if (source is MediaEntry.Input.Invalid) {
            mutable.update { it.copy(inputError = source.message) }; return
        }
        val url = (source as MediaEntry.Input.Link).url
        abandonChooser()
        discardPrepared()
        currentRecord = null
        mutable.update { it.copy(phase = HomePhase.STAGING, status = "Adding link to the durable queue…") }
        job = scope.launch {
            try {
                val row = repository.stage(newTaskId(), url)
                observer.event(row.id, "QUEUE", "staged", mapOf("sourceRetained" to (row.sourceUrl != null).toString()))
                mutable.update { it.copy(phase = HomePhase.READY, title = null, qualities = emptyList(), audioChoices = emptyList(),
                    selectedQuality = null, selectedAudioFormatId = null, status = if (row.sourceUrl != null)
                        "Link queued. Review it when ready; nothing started automatically."
                    else "Link recorded without its sensitive query. Re-enter it to start; it cannot be resumed after restart.") }
            } catch (canceled: CancellationException) { throw canceled }
            catch (error: Exception) { fail(null, error, Stage.ANY) }
        }
    }

    fun reviewTask(id: String, acceptPublicationRetry: Boolean = false) {
        val repository = queue ?: return
        if (closed || state.value.busy || job?.isActive == true || !state.value.engineReady) return
        abandonChooser()
        discardPrepared()
        mutable.update { it.copy(phase = HomePhase.STAGING, status = "Loading queued link…") }
        job = scope.launch {
            try {
                val row = repository.get(id)
                if (row == null) {
                    mutable.update { it.copy(phase = HomePhase.READY, status = "This task record is no longer available.") }; return@launch
                }
                if (row.phase in setOf(TaskPhase.DONE, TaskPhase.PUBLISHING, TaskPhase.DOWNLOADING, TaskPhase.RESOLVING)) {
                    mutable.update { it.copy(phase = HomePhase.READY, status = "This task cannot be restarted from the queue.") }; return@launch
                }
                if (row.phase == TaskPhase.FAILED) {
                    val code = runCatching { ErrorCode.valueOf(row.reason.orEmpty()) }.getOrNull()
                    if (code == null || !ErrorRecovery.forCode(code).freshResolve) {
                        mutable.update { it.copy(phase = HomePhase.READY, status = code?.let { ErrorRecovery.forCode(it).explanation }
                            ?: "This failure has no supported retry. Check Diagnostics.") }; return@launch
                    }
                    if (code == ErrorCode.NETWORK_TIMEOUT && currentRecord == row.id && clockMs() < retryAfterMs) {
                        mutable.update { it.copy(phase = HomePhase.READY, status = ErrorRecovery.forCode(code).explanation) }; return@launch
                    }
                }
                if (row.reason == "PUBLICATION_INTERRUPTED" && !acceptPublicationRetry) {
                    currentRecord = row.id
                    mutable.update { it.copy(phase = HomePhase.READY, publicationReviewRequired = true,
                        url = row.sourceUrl ?: "", failureCode = null,
                        status = "Saving was interrupted. Check Downloads for an existing file before explicitly confirming a new attempt.") }; return@launch
                }
                if (acceptPublicationRetry && row.reason == "PUBLICATION_INTERRUPTED") {
                    mutable.update { it.copy(publicationReviewRequired = false, failureCode = null) }
                }
                mutable.update { it.copy(phase = HomePhase.READY) }
                if (row.sourceUrl == null) {
                    editUrl("")
                    mutable.update { it.copy(status = "This record has no retained source link. Paste the original link again.") }; return@launch
                }
                editUrl(row.sourceUrl)
                currentRecord = row.id
                reviewedRecord = row
            } catch (canceled: CancellationException) { throw canceled }
            catch (error: Exception) { fail(null, error, Stage.ANY); return@launch }
            // Finish this load job before normal resolve admission checks.
            job = null
            resolve(forceRefresh = true)
        }
    }

    fun removeTask(id: String) {
        val repository = queue ?: return
        if (closed || state.value.busy || job?.isActive == true) return
        scope.launch {
            try { if (repository.remove(id)) observer.event(id, "QUEUE", "removed") }
            catch (canceled: CancellationException) { throw canceled }
            catch (error: Exception) { observer.event(id, "QUEUE", "write_failed") }
        }
    }

    private suspend fun persistPhase(recordId: String?, phase: TaskPhase, selection: TaskSelection? = null,
        title: String? = null, saved: PublishedMedia? = null): TaskRecord? {
        if (queue == null || recordId == null) return null
        return queue.transition(recordId, phase, selection = selection, title = title,
            savedUri = saved?.uri, savedName = saved?.name, savedMime = saved?.mimeType)
    }

    private suspend fun persistTerminal(recordId: String?, phase: TaskPhase, reason: String) = withContext(NonCancellable) {
        if (queue == null || recordId == null) return@withContext
        try {
            val previous = queue.get(recordId) ?: return@withContext
            if (previous.phase == phase && previous.reason == reason) return@withContext
            if (previous.phase != TaskPhase.DONE) queue.transition(recordId, phase, reason = reason)
        } catch (error: Exception) { observer.event(recordId, "QUEUE", "write_failed") }
    }

    private fun abandonChooser() {
        val chooser = prepared ?: return
        val row = chooser.recordId ?: return
        val revision = chooser.chooserRevision ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) {
            try { queue?.dismissChooser(row, revision) }
            catch (error: Exception) { observer.event(row, "QUEUE", "write_failed") }
        } }
    }

    private fun checkStorage(report: StorageReport, task: String, stage: Stage) {
        mutable.update { it.copy(storageReport = report) }
        observer.event(task, stage.name, "storage_check", mapOf("availableBytes" to report.availableBytes.toString(),
            "requiredBytes" to report.requiredBytes.toString(), "estimatedBytes" to (report.estimatedBytes?.toString() ?: "unknown"),
            "allowed" to report.allowed.toString()))
        if (!report.allowed) throw EngineFailure(DownloadError(ErrorCode.NO_SPACE, stage))
    }
}
