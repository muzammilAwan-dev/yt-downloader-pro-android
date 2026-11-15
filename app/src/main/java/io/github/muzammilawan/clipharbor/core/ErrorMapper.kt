package io.github.muzammilawan.clipharbor.core

/**
 * Maps a failed yt-dlp run to one code from `contracts/errors.json`.
 *
 * The input is the tail of yt-dlp's own output; patterns are matched case-insensitively and the first match wins.
 * Anything unmatched falls back to the closest contract code for that stage — never to a new, invented code.
 */
object ErrorMapper {
    fun fromExit(
        stage: Stage,
        exitCode: Int,
        output: String,
        maxTailLines: Int = 40,
    ): DownloadError {
        val lower = output.lowercase()
        val tail = output.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(maxTailLines)
        val code = classify(stage, lower)
        return DownloadError(
            code = code,
            stage = stage,
            exitCode = exitCode,
            stderrTail = tail,
        )
    }

    fun classify(stage: Stage, lowerOutput: String): ErrorCode {
        // Order matters: the most specific signal first.
        if (lowerOutput.contains("no supported javascript runtime") ||
            lowerOutput.contains("js runtime") && lowerOutput.contains("not available") ||
            lowerOutput.contains("jsc: no supported")
        ) {
            return ErrorCode.JS_RUNTIME_MISSING
        }
        if (lowerOutput.contains("no space left on device") || lowerOutput.contains("errno 28")) {
            return ErrorCode.NO_SPACE
        }
        if (lowerOutput.contains("403") &&
            (lowerOutput.contains("forbidden") || lowerOutput.contains("http error 403")) &&
            stage != Stage.MERGING
        ) {
            return if (stage == Stage.DOWNLOADING) ErrorCode.HTTP_403_ON_MEDIA else ErrorCode.FORMATS_WITHHELD
        }
        if (lowerOutput.contains("sign in to confirm") ||
            lowerOutput.contains("confirm you're not a bot") ||
            lowerOutput.contains("confirm you are not a bot") ||
            lowerOutput.contains("login required") ||
            lowerOutput.contains("cookies are needed")
        ) {
            return ErrorCode.AGE_OR_BOT_GATE
        }
        if (lowerOutput.contains("available in your country") || lowerOutput.contains("geo restriction")) {
            return ErrorCode.GEO_BLOCKED
        }
        if (lowerOutput.contains("requested format is not available") ||
            lowerOutput.contains("requested format not available") ||
            lowerOutput.contains("some formats are missing")
        ) {
            return ErrorCode.FORMATS_WITHHELD
        }
        if (lowerOutput.contains("private video") ||
            lowerOutput.contains("video unavailable") ||
            lowerOutput.contains("has been removed") ||
            lowerOutput.contains("this video is not available") ||
            lowerOutput.contains("unsupported url")
        ) {
            return ErrorCode.UNAVAILABLE
        }
        if (lowerOutput.contains("temporary failure in name resolution") ||
            lowerOutput.contains("name or service not known") ||
            lowerOutput.contains("network is unreachable") ||
            lowerOutput.contains("connection reset") ||
            lowerOutput.contains("connection refused") ||
            lowerOutput.contains("getaddrinfo")
        ) {
            return ErrorCode.NETWORK_OFFLINE
        }
        if (lowerOutput.contains("timed out") || lowerOutput.contains("timeout")) {
            return if (stage == Stage.RESOLVING) ErrorCode.RESOLVE_TIMEOUT else ErrorCode.NETWORK_TIMEOUT
        }
        return when (stage) {
            Stage.MERGING -> ErrorCode.MERGE_FAILED
            Stage.POST_PROCESSING -> ErrorCode.POST_PROCESS_FAILED
            Stage.PUBLISHING -> ErrorCode.STORAGE_PERMISSION_LOST
            Stage.RESOLVING -> ErrorCode.ENGINE_OUTDATED
            Stage.DOWNLOADING -> ErrorCode.NETWORK_TIMEOUT
            Stage.ANY -> ErrorCode.UNAVAILABLE
        }
    }
}
