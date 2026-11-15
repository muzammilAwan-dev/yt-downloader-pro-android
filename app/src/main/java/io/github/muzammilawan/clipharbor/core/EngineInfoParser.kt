package io.github.muzammilawan.clipharbor.core

/**
 * Best-effort parser for the `[debug]` header that yt-dlp prints with `-v`.
 *
 * yt-dlp's exact wording changes between releases, so every field is optional and the diagnostic report prints `?`
 * when a value could not be parsed. This is deliberately not a substitute for the `versions` shown by the app itself.
 */
object EngineInfoParser {
    private val python = Regex("""Python\s+(\d+\.\d+(?:\.\d+)?)""")
    private val ytdlp = Regex("""yt-dlp version\s+(?:[a-z]+@)?(\S+)""", RegexOption.IGNORE_CASE)
    private val exeVersions = Regex("""exe versions:\s*(.+)""", RegexOption.IGNORE_CASE)
    private val jsRuntimes = Regex("""JS runtimes?:\s*(.+)""", RegexOption.IGNORE_CASE)
    private val ejs = Regex("""yt[-_]dlp[-_]ejs(?:\s+version)?[\s:@-]*v?(\d+\.\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val ejsFallback = Regex("""\bejs\s+v?(\d+\.\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<String>, libraryVersion: String?): EngineInfo {
        val text = lines.joinToString("\n")
        val exe = exeVersions.find(text)?.groupValues?.get(1).orEmpty()
        val runtimeLine = jsRuntimes.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return EngineInfo(
            libraryVersion = libraryVersion?.let(::normalizeVersion),
            ytdlpVersion = ytdlp.find(text)?.groupValues?.get(1)?.let(::normalizeVersion),
            pythonVersion = python.find(text)?.groupValues?.get(1),
            ejsVersion = ejs.find(text)?.groupValues?.get(1) ?: ejsFallback.find(text)?.groupValues?.get(1),
            jsRuntime = runtimeLine.split(',')
                .firstOrNull { entry ->
                    entry.isNotBlank() && !entry.trim().startsWith("none", ignoreCase = true) &&
                        !entry.contains("(unknown)", ignoreCase = true) && !entry.contains("(unsupported)", ignoreCase = true)
                }
                ?.substringBefore('(')?.trim(),
            ffmpegVersion = toolVersion(exe, "ffmpeg"),
            ffprobeVersion = toolVersion(exe, "ffprobe"),
        )
    }

    /**
     * The library's `version(context)` returns the value wrapped in parentheses (`(2025.11.12)`), which the Phase 0
     * device report showed verbatim. Keep the number only.
     */
    fun normalizeVersion(raw: String): String =
        raw.trim().trim('(', ')').trim().removePrefix("v")

    /** `ffmpeg 7.1 (setts), ffprobe 7.1` -> `7.1` for "ffmpeg". */
    private fun toolVersion(exeLine: String, tool: String): String? =
        Regex("""\b""" + Regex.escape(tool) + """\s+(\S+)""")
            .find(exeLine)
            ?.groupValues?.get(1)
            ?.trimEnd(',')
            ?.takeIf { it.isNotBlank() }
}
