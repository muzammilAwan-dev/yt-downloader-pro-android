package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.QualityChoice
import io.github.muzammilawan.clipharbor.core.VideoQualities
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.core.VideoQualityPlan
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomePhase { INITIALIZING, READY, RESOLVING, CHOOSING, DOWNLOADING, PUBLISHING, DONE, FAILED, CANCELED }

data class HomeState(
    val phase: HomePhase = HomePhase.INITIALIZING,
    val engineReady: Boolean = false,
    val url: String = "",
    val inputError: String? = null,
    val title: String? = null,
    val qualities: List<QualityChoice> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val progress: Int? = null,
    val status: String = "Preparing the bundled engine…",
    val saved: PublishedMedia? = null,
    val pendingUrl: String? = null,
) {
    val busy: Boolean get() = phase in setOf(HomePhase.INITIALIZING, HomePhase.RESOLVING, HomePhase.DOWNLOADING, HomePhase.PUBLISHING)
    // Do not accidentally expose source/signed URLs or media titles when debugging an object.
    override fun toString(): String = "HomeState(phase=$phase)"
}

data class PublishedMedia(val uri: String, val name: String, val location: String, val mimeType: String)

interface HomeObserver {
    fun event(task: String?, stage: String, event: String, fields: Map<String, String> = emptyMap()) {}
    fun failure(task: String?, error: DownloadError) {}
    fun line(task: String, line: String) {}
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
) {
    private val mutable = MutableStateFlow(HomeState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var prepared: Prepared? = null
    @Volatile private var activeTask: String? = null
    @Volatile private var closed = false

    private class Prepared(val task: String, val url: String, val snapshot: ResolvedSnapshot?, val createdAt: Long,
        val plans: Map<VideoQuality, VideoQualityPlan>)

    fun initialize() {
        if (closed || job?.isActive == true || state.value.engineReady) return
        mutable.update { it.copy(phase = HomePhase.INITIALIZING, status = "Preparing the bundled engine…") }
        job = scope.launch {
            try {
                engine.init().getOrThrow()
                observer.event(null, "APP", "engine_init")
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
            title = null, qualities = emptyList(), selectedQuality = null, saved = null,
            phase = HomePhase.READY, status = "Review the link, then find formats.") }
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
                        qualities = emptyList(), selectedQuality = null, saved = null,
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
            saved = null, title = null, qualities = emptyList(), selectedQuality = null, progress = null,
            status = "Finding available video and audio formats…") }
        job = scope.launch {
            var snapshot: ResolvedSnapshot? = null
            try {
                observer.event(task, Stage.RESOLVING.name, "start")
                val outcome = engine.resolveForTask(url, task).getOrThrow()
                snapshot = outcome.resolvedSnapshot
                currentCoroutineContext().ensureActive()
                val qualities = VideoQualities.choices(outcome.media.formats)
                val plans = qualities.filter { it.available }.associate { choice ->
                    choice.quality to checkNotNull(VideoQualities.plan(outcome.media.formats, choice.quality))
                }
                if (plans.isEmpty()) throw EngineFailure(DownloadError(ErrorCode.FORMATS_WITHHELD, Stage.RESOLVING))
                prepared = Prepared(task, url, snapshot, clockMs(), plans)
                snapshot = null // Ownership is transferred to the chooser, not persisted in UI/history.
                observer.event(task, Stage.RESOLVING.name, "ok", mapOf("formats" to outcome.media.formats.size.toString()))
                mutable.update { it.copy(phase = HomePhase.CHOOSING, title = outcome.media.title,
                    qualities = qualities, selectedQuality = VideoQualities.default(qualities),
                    status = "Choose a video quality limit. Audio is included.") }
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
            mutable.update { it.copy(selectedQuality = quality) }
        }
    }

    fun download() {
        if (closed || state.value.phase != HomePhase.CHOOSING) return
        val input = prepared ?: return
        val quality = state.value.selectedQuality ?: return
        val plan = input.plans[quality] ?: return
        if (clockMs() - input.createdAt !in 0..ResolvedSnapshot.MAX_AGE_MS) {
            discardPrepared()
            resolve()
            return // Refresh requires another explicit download confirmation.
        }
        prepared = null
        activeTask = input.task
        mutable.update { it.copy(phase = HomePhase.DOWNLOADING, progress = null, status = "Downloading video and audio…") }
        // Enter the cleanup block before returning to the caller, even if the owner scope is canceled.
        // Android admission starts from this explicit user action; engine work still dispatches to IO.
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val directory = File(stagingRoot, input.task)
            var lease: DownloadForeground.Lease? = null
            try {
                currentCoroutineContext().ensureActive()
                val admitted = foreground.acquire(input.task)
                lease = admitted
                currentCoroutineContext().ensureActive()
                observer.event(input.task, Stage.DOWNLOADING.name, "start", mapOf(
                    "requestedResolution" to plan.quality.pixels.toString(), "qualityAxis" to "shortEdge",
                    "resolveReuse" to (input.snapshot != null).toString(),
                    "concurrentFragments" to "4"))
                val outcome = engine.download(DownloadRequest(input.url, plan.quality.pixels, directory, input.task,
                    mergeContainer = if (plan.quality.pixels >= 1440) "mkv" else "mp4/mkv",
                    resolvedSnapshot = input.snapshot, qualityPlan = plan), onProgress = { event ->
                    event.percent?.takeIf { it.isFinite() && it in 0f..100f }?.toInt()?.let { percent ->
                        if (!closed && activeTask == input.task && state.value.phase == HomePhase.DOWNLOADING) {
                            mutable.update {
                                if (!closed && activeTask == input.task && it.phase == HomePhase.DOWNLOADING) it.copy(progress = percent) else it
                            }
                            admitted.progress(percent)
                        }
                    }
                }, onLine = { if (!closed && activeTask == input.task) observer.line(input.task, it) }).getOrThrow()
                currentCoroutineContext().ensureActive()
                val file = outcome.files.singleOrNull()?.takeIf { it.isFile && it.length() > 0 }
                    ?: throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING))
                observer.event(input.task, Stage.DOWNLOADING.name, "selected_formats", mapOf(
                    "ids" to outcome.selectedFormatIds.joinToString("+").ifEmpty { "unknown" },
                    "actualHeight" to (outcome.selectedHeight?.toString() ?: "unknown"),
                    "actualWidth" to (outcome.selectedWidth?.toString() ?: "unknown"),
                    "actualResolution" to (if (outcome.selectedWidth != null && outcome.selectedHeight != null)
                        minOf(outcome.selectedWidth, outcome.selectedHeight).toString() else "unknown"),
                    "requestedResolution" to plan.quality.pixels.toString(), "qualityAxis" to "shortEdge",
                    "reusedResolve" to outcome.reusedResolve.toString()))
                mutable.update { it.copy(phase = HomePhase.PUBLISHING, progress = null, status = "Saving the finished file…") }
                admitted.publishing()
                observer.event(input.task, Stage.PUBLISHING.name, "start")
                currentCoroutineContext().ensureActive()
                val saved = publish(file)
                observer.event(input.task, Stage.PUBLISHING.name, "ok", mapOf("uri" to saved.uri,
                    "bytes" to file.length().toString(), "mime" to saved.mimeType))
                val width = outcome.selectedWidth
                val height = outcome.selectedHeight
                val actual = if (width != null && height != null) "$width × $height (${minOf(width, height)}p short edge)"
                    else "dimensions not fully reported"
                val ids = outcome.selectedFormatIds.joinToString("+").ifEmpty { "unreported" }
                mutable.update { it.copy(phase = HomePhase.DONE, saved = saved,
                    status = "Saved. Selected video: $actual · formats $ids.") }
                // Success only; cancellation does not prove native descendants have released staging files.
                directory.deleteRecursively()
            } catch (canceled: CancellationException) {
                canceled(input.task)
                throw canceled
            } catch (error: Exception) {
                fail(input.task, error, if (state.value.phase == HomePhase.PUBLISHING) Stage.PUBLISHING else Stage.DOWNLOADING)
            } finally {
                try {
                    lease?.close()
                } finally {
                    input.snapshot?.close()
                    activeTask = null
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
        if (!state.value.busy) mutable.update { it.copy(phase = HomePhase.CANCELED, status = "Selection dismissed.") }
    }

    fun close() {
        closed = true
        activeTask?.let(engine::cancel)
        job?.cancel()
        discardPrepared()
    }

    private fun discardPrepared() {
        prepared?.snapshot?.close()
        prepared = null
    }

    private fun canceled(task: String) {
        if (!closed) {
            observer.event(task, "ANY", "canceled")
            mutable.update { it.copy(phase = HomePhase.CANCELED, progress = null,
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
            ErrorCode.FORMATS_WITHHELD -> "No selectable video + audio qualities were reported. Audio-only selection is not available yet."
            ErrorCode.RESOLVE_TIMEOUT, ErrorCode.HTTP_403_ON_MEDIA -> "The link or media access may have expired. Find formats again."
            ErrorCode.NO_SPACE -> "Not enough storage. Free some space, then find formats again."
            else -> "This attempt failed (${error.code.name}). Diagnostics has the details."
        }
        mutable.update { it.copy(phase = HomePhase.FAILED, progress = null, status = message) }
    }
}
