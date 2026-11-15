package io.github.muzammilawan.clipharbor.core

/**
 * One selectable format as reported by yt-dlp (`--dump-json` -> `formats[]`).
 *
 * Platform-free on purpose so format planning and reporting can be unit-tested on the JVM (docs/12).
 */
data class FormatInfo(
    val formatId: String,
    val ext: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val approxBytes: Long? = null,
    val note: String? = null,
    val protocol: String? = null,
) {
    val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"
    val isMuxed: Boolean get() = hasVideo && hasAudio
}

/** Resolved media metadata for one video (Phase 0: no playlists). */
data class MediaInfo(
    val id: String,
    val title: String,
    val uploader: String? = null,
    val durationSeconds: Int? = null,
    val webpageUrl: String? = null,
    val formats: List<FormatInfo> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Counts used by the diagnostics report (docs/12 §3 check 6). */
data class FormatSummary(
    val total: Int,
    val videoOnly: Int,
    val audioOnly: Int,
    val muxed: Int,
    val maxHeight: Int?,
    val maxHeightMuxed: Int?,
    val has1080p: Boolean,
    val hdUnavailable: Boolean,
) {
    companion object {
        fun of(formats: List<FormatInfo>, targetHeight: Int = 1080): FormatSummary {
            val video = formats.filter { it.hasVideo }
            val audio = formats.filter { it.hasAudio && !it.hasVideo }
            val muxed = formats.filter { it.isMuxed }
            val maxHeight = video.mapNotNull { it.height }.maxOrNull()
            return FormatSummary(
                total = formats.size,
                videoOnly = formats.count { it.hasVideo && !it.hasAudio },
                audioOnly = audio.size,
                muxed = muxed.size,
                maxHeight = maxHeight,
                maxHeightMuxed = muxed.mapNotNull { it.height }.maxOrNull(),
                has1080p = video.any { (it.height ?: 0) >= targetHeight },
                hdUnavailable = formats.isNotEmpty() && (maxHeight ?: 0) < 720,
            )
        }
    }
}

/** Engine versions, parsed from yt-dlp `-v` output (see [EngineInfoParser]). */
data class EngineInfo(
    val libraryVersion: String? = null,
    val ytdlpVersion: String? = null,
    val pythonVersion: String? = null,
    val ejsVersion: String? = null,
    val jsRuntime: String? = null,
    val ffmpegVersion: String? = null,
    val ffprobeVersion: String? = null,
) {
    fun hasJsRuntime(): Boolean = !jsRuntime.isNullOrBlank()
    fun hasFfmpeg(): Boolean = !ffmpegVersion.isNullOrBlank()
    fun headerLine(): String = buildString {
        append("library=").append(libraryVersion ?: "?")
        append(" ytdlp=").append(ytdlpVersion ?: "?")
        append(" python=").append(pythonVersion ?: "?")
        // A missing version line does not prove presence, absence, or a particular bundled version.
        append(" ejs=").append(ejsVersion ?: "unknown")
        append(" jsRuntime=").append(jsRuntime ?: "missing")
        append(" ffmpeg=").append(ffmpegVersion ?: "missing")
    }
}

/** Progress callback payload; [percent] is null when yt-dlp prints a non-progress line. */
data class ProgressEvent(val percent: Float?, val etaSeconds: Long?, val line: String)

data class DownloadRequest(
    val url: String,
    val maxHeight: Int = 1080,
    val outputDirectory: java.io.File,
    val processId: String,
    val mergeContainer: String = "mp4/mkv",
)

data class DownloadOutcome(
    val files: List<java.io.File>,
    val exitCode: Int,
    val elapsedMs: Long,
    val outputTail: List<String>,
)

data class ResolveOutcome(
    val media: MediaInfo,
    val engineInfo: EngineInfo,
    val debugTail: List<String>,
)

/** Stages from `contracts/errors.json`. */
enum class Stage { RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING, ANY }

/** Error codes from `contracts/errors.json` (v1); keep the names identical. */
enum class ErrorCode(val retryable: Boolean) {
    ENGINE_OUTDATED(true),
    JS_RUNTIME_MISSING(false),
    FORMATS_WITHHELD(true),
    LOGIN_REQUIRED(true),
    AGE_OR_BOT_GATE(true),
    GEO_BLOCKED(false),
    UNAVAILABLE(false),
    RESOLVE_TIMEOUT(true),
    HTTP_403_ON_MEDIA(true),
    NETWORK_OFFLINE(true),
    NETWORK_TIMEOUT(true),
    NO_SPACE(true),
    STORAGE_PERMISSION_LOST(true),
    MERGE_FAILED(true),
    POST_PROCESS_FAILED(true),
    FGS_TIMEOUT(true),
    CANCELED(false),
}

data class DownloadError(
    val code: ErrorCode,
    val stage: Stage,
    val detail: String? = null,
    val exitCode: Int? = null,
    val stderrTail: List<String> = emptyList(),
) {
    val retryable: Boolean get() = code.retryable

    /** Greppable single line for the diagnostic log. */
    fun logFields(): String = buildString {
        append("code=").append(code.name)
        append(" stage=").append(stage.name)
        append(" retryable=").append(retryable)
        exitCode?.let { append(" exit=").append(it) }
        detail?.let { append(" detail=\"").append(it.replace('"', '\'').take(300)).append('"') }
    }
}

/**
 * The engine boundary (docs/02-ARCHITECTURE.md §3). Phase 0 keeps it small: init/version, resolve, download.
 * Business logic is tested with a fake implementation; only [io.github.muzammilawan.clipharbor.engine.YoutubeDlEngine]
 * touches youtubedl-android and the device.
 */
interface Engine {
    /** Human-readable engine id for logs (`youtubedl-android` or `fake`). */
    val id: String

    /** Unpacks/initialises the engine. Safe to call repeatedly. */
    suspend fun init(): Result<EngineInfo>

    /** Last known engine info; never performs work. */
    fun info(): EngineInfo

    /** Resolves one video URL. */
    suspend fun resolve(url: String): Result<ResolveOutcome>

    /** Downloads and merges one video; [onProgress] is called from a background thread. */
    suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressEvent) -> Unit,
        onLine: (String) -> Unit = {},
    ): Result<DownloadOutcome>

    /** Best-effort cancel of a running download (process id from [DownloadRequest]). */
    fun cancel(processId: String): Boolean
}
