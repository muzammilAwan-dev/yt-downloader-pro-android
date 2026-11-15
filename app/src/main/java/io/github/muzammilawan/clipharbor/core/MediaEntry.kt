package io.github.muzammilawan.clipharbor.core

import java.net.URI

/** User input is data only. No intent extras or clipboard text become yt-dlp options. */
object MediaEntry {
    const val MAX_INPUT_CHARS = 8192

    sealed interface Input {
        class Link internal constructor(val url: String) : Input {
            override fun toString(): String = "Link(<private>)"
        }
        data class Invalid(val message: String) : Input
    }

    fun pasted(text: String): Input {
        if (text.length > MAX_INPUT_CHARS) return Input.Invalid("This link is too long.")
        val url = text.trim()
        if (url.isEmpty()) return Input.Invalid("Paste a full HTTPS link first.")
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.any { it.isWhitespace() || it.isISOControl() || it == '\\' } ||
            uri == null || !uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port !in 1..65535)
        ) return Input.Invalid("Use one full HTTPS link without account credentials or options.")
        return Input.Link(url)
    }

    /** A title may accompany a share, but exactly one web link must be present. Never auto-resolve. */
    fun shared(text: String): Input {
        if (text.length > MAX_INPUT_CHARS) return Input.Invalid("The shared text is too long.")
        if (text.any { it.isISOControl() && it !in "\r\n\t" }) {
            return Input.Invalid("The shared text contains unsupported characters.")
        }
        val links = WEB_LINK.findAll(text).map { it.value }.toList()
        if (links.size != 1) return Input.Invalid("Share exactly one HTTPS link at a time.")
        return pasted(links.single())
    }

    /** Video + audio only in this increment. Heights are ceilings, not promised exact format IDs. */
    fun videoHeights(formats: List<FormatInfo>): List<Int> {
        val separateAudio = formats.any { it.hasAudio && !it.hasVideo }
        return formats.asSequence()
            .filter { it.hasVideo && (it.hasAudio || separateAudio) }
            .mapNotNull { it.height?.takeIf { height -> height > 0 } }
            .distinct().sortedDescending().toList()
    }

    fun defaultHeight(heights: List<Int>): Int? = heights.filter { it <= 1080 }.maxOrNull() ?: heights.minOrNull()

    private val WEB_LINK = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
}
