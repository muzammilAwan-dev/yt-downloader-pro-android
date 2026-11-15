package io.github.muzammilawan.clipharbor.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.EngineInfo
import io.github.muzammilawan.clipharbor.core.EngineInfoParser
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ErrorMapper
import io.github.muzammilawan.clipharbor.core.FormatInfo
import io.github.muzammilawan.clipharbor.core.FormatPlanner
import io.github.muzammilawan.clipharbor.core.MediaInfo
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolveOutcome
import io.github.muzammilawan.clipharbor.core.Stage
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The only class that talks to youtubedl-android. All work happens on [Dispatchers.IO]; yt-dlp itself runs in a child
 * process behind the library (Python + QuickJS + ffmpeg, see docs/RESEARCH-LOG.md R14-R21).
 */
class YoutubeDlEngine(private val context: Context) : Engine {
    override val id: String = "youtubedl-android"

    @Volatile
    private var cached: EngineInfo = EngineInfo(libraryVersion = LIBRARY_VERSION)

    @Volatile
    private var initialized: Boolean = false

    private val initLock = Any()

    override suspend fun init(): Result<EngineInfo> = withContext(Dispatchers.IO) {
        try {
            Result.success(initBlocking())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(
                error as? EngineFailure ?: EngineFailure(
                    DownloadError(ErrorCode.ENGINE_OUTDATED, Stage.ANY, detail = error.message),
                ),
            )
        }
    }

    private fun initBlocking(): EngineInfo {
        synchronized(initLock) {
            if (!initialized) {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                initialized = true
            }
            cached = cached.copy(
                libraryVersion = LIBRARY_VERSION,
                ytdlpVersion = cached.ytdlpVersion
                    ?: runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
                        ?.let(EngineInfoParser::normalizeVersion),
                jsRuntime = cached.jsRuntime ?: bundledLib("libqjs.so")?.let { "quickjs (bundled)" },
                ffmpegVersion = cached.ffmpegVersion ?: bundledLib("libffmpeg.zip.so")?.let { "bundled (libffmpeg)" },
                pythonVersion = cached.pythonVersion ?: bundledLib("libpython.zip.so")?.let { "bundled (python)" },
            )
            return cached
        }
    }

    private fun bundledLib(name: String): String? =
        File(context.applicationInfo.nativeLibraryDir, name).takeIf { it.exists() }?.name

    override fun info(): EngineInfo = cached

    override suspend fun resolve(url: String): Result<ResolveOutcome> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val request = YoutubeDLRequest(url)
                .addOption("-v")
                .addOption("--dump-json")
                .addOption("--no-playlist")
            val response = YoutubeDL.getInstance().execute(request, null)
            val stderr = response.err
            if (response.exitCode != 0) {
                throw EngineFailure(
                    ErrorMapper.fromExit(
                        stage = Stage.RESOLVING,
                        exitCode = response.exitCode,
                        output = buildString {
                            append(stderr)
                            append('\n')
                            append(response.out.take(4000))
                        },
                    ),
                )
            }
            val jsonLine = response.out.lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("{") && it.endsWith("}") }
                ?: throw EngineFailure(
                    DownloadError(ErrorCode.ENGINE_OUTDATED, Stage.RESOLVING, detail = "no JSON in yt-dlp output"),
                )
            val media = parseMedia(jsonLine)
            val parsed = EngineInfoParser.parse(
                lines = stderr.lines() + response.out.lines(),
                libraryVersion = LIBRARY_VERSION,
            )
            cached = merge(cached, parsed)
            Result.success(
                ResolveOutcome(
                    media = media,
                    engineInfo = cached,
                    debugTail = stderr.lines().filter { it.contains("[debug]") }.takeLast(200),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error as? EngineFailure ?: EngineFailure(DownloadError(ErrorCode.ENGINE_OUTDATED, Stage.RESOLVING, detail = error.message)))
        }
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressEvent) -> Unit,
        onLine: (String) -> Unit,
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val outputDirectory = request.outputDirectory.apply { if (!exists()) mkdirs() }
            val startedAt = System.currentTimeMillis()
            val tail = ArrayDeque<String>()
            val ytRequest = YoutubeDLRequest(request.url)
                .addOption("-v")
                .addOption("-f", FormatPlanner.selector(request.maxHeight))
                .addOption("--merge-output-format", request.mergeContainer)
                .addOption("-P", outputDirectory.absolutePath)
                .addOption("-o", FormatPlanner.outputTemplate())
                .addOption("--windows-filenames")
                .addOption("--no-playlist")
                .addOption("--no-mtime")
                .addOption("--newline")
                .addOption("--progress")
                .addOption("--print", "after_move:filepath")
                .addOption("--no-simulate")

            val response = YoutubeDL.getInstance().execute(ytRequest, request.processId) { percent, eta, line ->
                if (line.isNotBlank()) {
                    synchronized(tail) {
                        tail.addLast(line)
                        while (tail.size > TAIL_LINES) tail.removeFirst()
                    }
                    onLine(line)
                }
                onProgress(ProgressEvent(percent.takeIf { it >= 0f }, eta, line))
            }

            val combined = response.err + "\n" + response.out
            if (response.exitCode != 0) {
                val stage = if (combined.contains("[Merger]")) Stage.MERGING else Stage.DOWNLOADING
                throw EngineFailure(ErrorMapper.fromExit(stage, response.exitCode, combined))
            }

            val files = findOutputFiles(outputDirectory, response.out, startedAt)
            if (files.isEmpty()) {
                throw EngineFailure(
                    DownloadError(
                        ErrorCode.POST_PROCESS_FAILED,
                        Stage.POST_PROCESSING,
                        detail = "exit=0 but no output file was found",
                        exitCode = response.exitCode,
                        stderrTail = synchronized(tail) { tail.toList() }.takeLast(40),
                    ),
                )
            }
            Result.success(
                DownloadOutcome(
                    files = files,
                    exitCode = response.exitCode,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    outputTail = synchronized(tail) { tail.toList() },
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(
                error as? EngineFailure ?: EngineFailure(
                    DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.DOWNLOADING, detail = error.message),
                ),
            )
        }
    }

    override fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    private fun ensureInitialized() {
        if (!initialized) initBlocking()
    }

    private fun findOutputFiles(dir: File, stdout: String, startedAt: Long): List<File> {
        val printed = stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .map { File(it) }
            .filter { it.isAbsolute && it.exists() && it.isFile }
            .toList()
        if (printed.isNotEmpty()) return printed

        val ignored = setOf("part", "ytdl", "temp")
        val candidates = dir.listFiles()
            ?.filter { it.isFile }
            ?.filter { it.extension.lowercase() !in ignored }
            ?.filter { it.lastModified() >= startedAt - 2000 }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        return candidates.takeLast(1)
    }

    private fun parseMedia(json: String): MediaInfo {
        val root = JSONObject(json)
        val type = root.optString("_type", "video")
        if (type != "video") {
            throw EngineFailure(
                DownloadError(
                    ErrorCode.UNAVAILABLE,
                    Stage.RESOLVING,
                    detail = "unsupported result type '$type' (playlists are Phase 2)",
                ),
            )
        }
        val formats = buildList {
            val array = root.optJSONArray("formats") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    FormatInfo(
                        formatId = item.optString("format_id"),
                        ext = item.optStringOrNull("ext"),
                        vcodec = item.optStringOrNull("vcodec"),
                        acodec = item.optStringOrNull("acodec"),
                        width = item.optIntOrNull("width"),
                        height = item.optIntOrNull("height"),
                        fps = item.optIntOrNull("fps"),
                        approxBytes = item.optLongOrNull("filesize") ?: item.optLongOrNull("filesize_approx"),
                        note = item.optStringOrNull("format_note"),
                        protocol = item.optStringOrNull("protocol"),
                    ),
                )
            }
        }
        return MediaInfo(
            id = root.optString("id", "unknown"),
            title = root.optString("title", "untitled"),
            uploader = root.optStringOrNull("uploader"),
            durationSeconds = root.optIntOrNull("duration"),
            webpageUrl = root.optStringOrNull("webpage_url"),
            formats = formats,
            warnings = emptyList(),
        )
    }

    private fun merge(base: EngineInfo, parsed: EngineInfo): EngineInfo = EngineInfo(
        libraryVersion = base.libraryVersion ?: parsed.libraryVersion,
        ytdlpVersion = parsed.ytdlpVersion ?: base.ytdlpVersion,
        pythonVersion = parsed.pythonVersion ?: base.pythonVersion,
        ejsVersion = parsed.ejsVersion ?: base.ejsVersion,
        jsRuntime = parsed.jsRuntime ?: base.jsRuntime,
        ffmpegVersion = parsed.ffmpegVersion ?: base.ffmpegVersion,
        ffprobeVersion = parsed.ffprobeVersion ?: base.ffprobeVersion,
    )

    companion object {
        /** Must match `ytdlpAndroid` in gradle/libs.versions.toml; the library does not expose its own version. */
        const val LIBRARY_VERSION = "0.18.1"

        private const val TAIL_LINES = 200
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null
