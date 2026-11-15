package io.github.muzammilawan.clipharbor.testing

import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.EngineInfo
import io.github.muzammilawan.clipharbor.core.FormatInfo
import io.github.muzammilawan.clipharbor.core.MediaInfo
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolveOutcome

/** Scriptable engine for JVM tests (docs/12 §1). Never touches yt-dlp, Android or the network. */
class FakeEngine(
    var initResult: Result<EngineInfo> = Result.success(SAMPLE_INFO),
    var resolveResult: Result<ResolveOutcome> = Result.success(SAMPLE_RESOLVE),
    var downloadResult: Result<DownloadOutcome> = Result.success(DownloadOutcome(emptyList(), 0, 0, emptyList())),
) : Engine {
    override val id: String = "fake"

    var initCalls: Int = 0
        private set
    var resolveCalls: Int = 0
        private set
    var cancelledProcessIds: MutableList<String> = mutableListOf()

    override suspend fun init(): Result<EngineInfo> {
        initCalls++
        return initResult
    }

    override fun info(): EngineInfo = initResult.getOrNull() ?: EngineInfo()

    override suspend fun resolve(url: String): Result<ResolveOutcome> {
        resolveCalls++
        return resolveResult
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressEvent) -> Unit,
        onLine: (String) -> Unit,
    ): Result<DownloadOutcome> {
        onProgress(ProgressEvent(100f, 0, "fake progress"))
        onLine("fake line")
        return downloadResult
    }

    override fun cancel(processId: String): Boolean {
        cancelledProcessIds += processId
        return true
    }

    companion object {
        val SAMPLE_INFO = EngineInfo(
            libraryVersion = "0.18.1",
            ytdlpVersion = "2025.11.12",
            pythonVersion = "3.8.20",
            ejsVersion = "0.3.0",
            jsRuntime = "quickjs-2021-03-27",
            ffmpegVersion = "7.1",
        )

        val SAMPLE_FORMATS = listOf(
            FormatInfo("137", ext = "mp4", vcodec = "avc1.640028", acodec = "none", width = 1920, height = 1080, fps = 30),
            FormatInfo("136", ext = "mp4", vcodec = "avc1.4d401f", acodec = "none", width = 1280, height = 720, fps = 30),
            FormatInfo("140", ext = "m4a", vcodec = "none", acodec = "mp4a.40.2", approxBytes = 3_000_000),
            FormatInfo("18", ext = "mp4", vcodec = "avc1.42001E", acodec = "mp4a.40.2", width = 640, height = 360),
        )

        val SAMPLE_MEDIA = MediaInfo(
            id = "dQw4w9WgXcQ",
            title = "Synthetic test entry",
            uploader = "synthetic",
            durationSeconds = 213,
            webpageUrl = "https://example.invalid/watch?v=dQw4w9WgXcQ",
            formats = SAMPLE_FORMATS,
        )

        val SAMPLE_RESOLVE = ResolveOutcome(
            media = SAMPLE_MEDIA,
            engineInfo = SAMPLE_INFO,
            debugTail = listOf("[debug] yt-dlp version stable@2025.11.12"),
        )
    }
}
