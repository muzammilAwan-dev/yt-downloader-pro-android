package io.github.muzammilawan.clipharbor.engine

import android.content.Context
import android.system.Os
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
import io.github.muzammilawan.clipharbor.core.MediaInfo
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ReplayInfo
import io.github.muzammilawan.clipharbor.core.ResolveOutcome
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.log.LogRedactor
import java.io.File
import java.io.InterruptedIOException
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

    private val snapshotDirectory = File(context.noBackupFilesDir, "resolved-inputs")
    private val runner = object : BlockingEngineRunner {
        override fun execute(
            command: EngineCommand,
            internalId: String,
            callback: ((Float, Long, String) -> Unit)?,
        ): EngineResponse {
            val request = YoutubeDLRequest(command.urls)
            command.options.forEach { (name, value) ->
                if (value == null) request.addOption(name) else request.addOption(name, value)
            }
            val response = YoutubeDL.getInstance().execute(
                request, internalId, redirectErrorStream = command.redirectErrorStream, callback = callback,
            )
            return EngineResponse(response.exitCode, response.out, response.err)
        }

        override fun destroy(internalId: String) {
            YoutubeDL.getInstance().destroyProcessById(internalId)
        }
    }
    private val execution = EngineExecution(runner::destroy)
    private val downloader = DownloadExecutor(runner, execution, snapshotDirectory)

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
            val pin = startup.initialize(
                install = {
                    val base = File(context.noBackupFilesDir, YoutubeDL.baseName)
                    val destination = File(File(base, YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)
                    // Same-directory rename is atomic on Android (API 21+); never delete the old engine first.
                    val installer = BundledEngineInstaller({ staged, target ->
                        Os.rename(staged.absolutePath, target.absolutePath)
                    })
                    installer.install(
                        destination, BundledEngineStartup.SHA256, BundledEngineStartup.BYTES,
                    ) { context.assets.open(BundledEngineStartup.ASSET) }
                },
                initializeLibraries = {
                    // Process-wide gate: activity recreation must not delete a still-running worker's input.
                    synchronized(snapshotCleanupLock) {
                        if (!snapshotsCleaned) {
                            ResolvedSnapshot.cleanOrphans(snapshotDirectory)
                            snapshotsCleaned = true
                        }
                    }
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                },
            )
            cached = cached.copy(
                libraryVersion = LIBRARY_VERSION,
                // Updater preferences do not describe our locally installed bytes. Later verbose output stays observable.
                ytdlpVersion = cached.ytdlpVersion ?: pin.ytdlpVersion,
                ejsVersion = cached.ejsVersion ?: pin.ejsVersion,
                jsRuntime = cached.jsRuntime ?: bundledLib("libqjs.so")?.let { "quickjs (bundled)" },
                ffmpegVersion = cached.ffmpegVersion ?: bundledLib("libffmpeg.zip.so")?.let { "bundled (libffmpeg)" },
                pythonVersion = cached.pythonVersion ?: bundledLib("libpython.zip.so")?.let { "bundled (python)" },
            )
            initialized = true
            return cached
        }
    }

    private fun bundledLib(name: String): String? =
        File(context.applicationInfo.nativeLibraryDir, name).takeIf { it.exists() }?.name

    override fun info(): EngineInfo = cached

    override suspend fun resolve(url: String): Result<ResolveOutcome> = resolveInternal(url, null)

    override suspend fun resolveForTask(url: String, processId: String): Result<ResolveOutcome> =
        resolveInternal(url, processId)

    private suspend fun resolveInternal(url: String, processId: String?): Result<ResolveOutcome> = withContext(Dispatchers.IO) {
        try {
            requireHttpsSource(url)
            ensureInitialized()
            val response = execution.run(processId) { internalId ->
                runner.execute(
                    EngineCommand(
                        listOf(url),
                        listOf("--ignore-config" to null, "-v" to null, "--dump-json" to null, "--no-playlist" to null),
                        redirectErrorStream = false,
                    ), internalId, null,
                )
            }
            val stderr = response.err
            if (response.exitCode != 0) {
                throw EngineFailure(
                    ErrorMapper.fromExit(
                        stage = Stage.RESOLVING,
                        exitCode = response.exitCode,
                        // stdout can be sensitive full info JSON; never put it in a diagnostic tail.
                        output = LogRedactor.redactLine(stderr),
                        maxTailLines = 200,
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
            val snapshot = processId?.let {
                val replay = JSONObject(jsonLine)
                val keys = replay.keys().asSequence().toSet()
                ReplayInfo.fieldsToDiscard(keys, (replay.optJSONArray("formats")?.length() ?: 0) > 0)
                    .forEach { key -> replay.remove(key) }
                ResolvedSnapshot(url, it, replay.toString())
            }
            Result.success(
                ResolveOutcome(
                    media = media,
                    engineInfo = cached,
                    debugTail = stderr.lines().filter { it.contains("[debug]") }.takeLast(200).map(LogRedactor::redactLine),
                    resolvedSnapshot = snapshot,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val safe = LogRedactor.redactLine(error.message.orEmpty())
            Result.failure(
                error as? EngineFailure ?: EngineFailure(
                    DownloadError(ErrorMapper.classify(Stage.RESOLVING, safe.lowercase()), Stage.RESOLVING,
                        detail = safe.take(300), stderrTail = safe.lines().takeLast(200)),
                ),
            )
        }
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressEvent) -> Unit,
        onLine: (String) -> Unit,
    ): Result<DownloadOutcome> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            downloader.download(request, onProgress, onLine)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(
                error as? EngineFailure ?: EngineFailure(
                    DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.DOWNLOADING, detail = error.message),
                ),
            )
        } finally {
            // Also handles init failure/cancellation before DownloadExecutor is entered.
            request.resolvedSnapshot?.close()
        }
    }

    override fun cancel(processId: String): Boolean = execution.cancel(processId)

    private fun ensureInitialized() {
        if (!initialized) initBlocking()
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

        private val startup = BundledEngineStartup()
        private val snapshotCleanupLock = Any()
        private var snapshotsCleaned = false
    }
}

/**
 * One instance is shared by every adapter in the process; callbacks must not execute engine tasks.
 * Only a fully successful startup publishes the verified pin. Failures can retry before any task is admitted.
 * Once ready, a recreated adapter cannot reinstall over an active worker; a new process verifies the file again.
 */
internal class BundledEngineStartup {
    private var installed: EngineInfo? = null

    @Synchronized
    fun initialize(install: () -> Unit, initializeLibraries: () -> Unit): EngineInfo {
        checkInterrupted()
        installed?.let { return it }
        install()
        checkInterrupted()
        initializeLibraries()
        checkInterrupted()
        return EngineInfo(ytdlpVersion = VERSION, ejsVersion = EJS_VERSION).also { installed = it }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Bundled engine startup interrupted")
    }

    companion object {
        const val ASSET = "engine/yt-dlp-2026.10.26"
        const val VERSION = "2026.10.26"
        const val EJS_VERSION = "0.8.0"
        const val BYTES = 3_073_072L
        const val SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null
