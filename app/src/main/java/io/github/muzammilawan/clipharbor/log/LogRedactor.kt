package io.github.muzammilawan.clipharbor.log

/**
 * Removes secrets from anything written to the diagnostic log (docs/12 §4). Unit-tested with realistic samples;
 * callers should route **all** log text through here, including raw yt-dlp output.
 */
object LogRedactor {
    const val REDACTED = "<redacted>"

    // Include an existing marker in the match so a second pass processes the whole URL, not just its prefix.
    private val url = Regex("""https?://(?:[^\s"'<>]|<redacted>)+""", RegexOption.IGNORE_CASE)

    private val headerSecret = Regex(
        """(?i)\b((?:set-)?cookie|authorization|proxy-authorization|x-goog-authuser)\s*:\s*([^\r\n]+)""",
    )

    private val poToken = Regex("""(?i)\b(po_?token|potoken|pot)\s*[:=]\s*([^\s&,;"']+)""")

    // Netscape cookie file: domain \t flag \t path \t secure \t expiry \t name \t value
    private val netscapeCookieLine = Regex(
        """(?m)^((?:#HttpOnly_)?[^\t#\r\n][^\t\r\n]*\t[^\t\r\n]*\t[^\t\r\n]*\t[^\t\r\n]*\t[^\t\r\n]*\t[^\t\r\n]*\t)([^\t\r\n]+)$""",
    )

    private val cookiePair = Regex("""(?i)\b(SAPISID|APISID|SSID|HSID|SID|__Secure-[13]PAPISID|__Secure-[13]PSID)=([^;\s]+)""")
    private val email = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")

    fun redactLine(line: String): String {
        var out = headerSecret.replace(line) { m -> "${m.groupValues[1]}: $REDACTED" }
        out = netscapeCookieLine.replace(out) { m -> m.groupValues[1] + REDACTED }
        out = url.replace(out) { m ->
            val raw = m.value
            val trailing = raw.takeLastWhile { it == ')' || it == ',' || it == '.' || it == ']' }
            redactUrl(raw.dropLast(trailing.length)) + trailing
        }
        out = poToken.replace(out) { m -> "${m.groupValues[1]}=$REDACTED" }
        out = cookiePair.replace(out) { m -> "${m.groupValues[1]}=$REDACTED" }
        out = email.replace(out, REDACTED)
        return out
    }

    /**
     * Keeps scheme, host and path; drops credentials/fragments and replaces the query with `?<redacted>`.
     *
     * A page URL may keep its `v=` parameter so a log reader can still identify the video; every other parameter is
     * dropped because media URLs carry expiring signatures and PO tokens there.
     */
    fun redactUrl(rawUrl: String, keepVideoId: Boolean = true): String {
        val schemeEnd = rawUrl.indexOf("://")
        if (schemeEnd <= 0) return rawUrl
        val withoutFragment = rawUrl.substringBefore('#')
        val queryStart = withoutFragment.indexOf('?')
        val pathEnd = listOfNotNull(
            queryStart.takeIf { it >= 0 },
        ).minOrNull() ?: withoutFragment.length
        val rawBase = withoutFragment.substring(0, pathEnd)
        val authorityStart = schemeEnd + 3
        val authorityEnd = rawBase.indexOf('/', authorityStart).takeIf { it >= 0 } ?: rawBase.length
        val authority = rawBase.substring(authorityStart, authorityEnd).substringAfterLast('@')
        val path = rawBase.substring(authorityEnd)
        val base = rawBase.substring(0, authorityStart) + authority + path
        if (queryStart < 0) return base
        val query = withoutFragment.substring(queryStart + 1)
        val host = authority.substringBefore(':').lowercase()
        val isWatchPage = (host == "youtube.com" || host.endsWith(".youtube.com")) && path == "/watch"
        val kept = if (keepVideoId && isWatchPage) {
            query.split('&')
                .firstOrNull { it.startsWith("v=") && it.substring(2).matches(Regex("[A-Za-z0-9_-]{1,40}")) }
                .orEmpty()
        } else {
            ""
        }
        return if (kept.isEmpty()) "$base?$REDACTED" else "$base?$kept&$REDACTED"
    }
}
