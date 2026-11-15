package io.github.muzammilawan.clipharbor.log

/**
 * Removes secrets from anything written to the diagnostic log (docs/12 §4). Unit-tested with realistic samples;
 * callers should route **all** log text through here, including raw yt-dlp output.
 */
object LogRedactor {
    const val REDACTED = "<redacted>"

    private val url = Regex("""https?://[^\s"'<>]+""")

    private val headerSecret = Regex(
        """(?i)\b((?:set-)?cookie|authorization|proxy-authorization|x-goog-authuser)\s*:\s*([^\r\n]+)""",
    )

    private val poToken = Regex("""(?i)\b(po_?token|potoken|pot)\s*[:=]\s*([^\s&,;"']+)""")

    // Netscape cookie file: domain \t flag \t path \t secure \t expiry \t name \t value
    private val netscapeCookieLine = Regex(
        """(?m)^([^\t#\r\n][^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t)([^\t\r\n]+)$""",
    )

    private val cookiePair = Regex("""(?i)\b(SAPISID|APISID|SSID|HSID|SID|__Secure-[13]PAPISID|__Secure-[13]PSID)=([^;\s]+)""")

    fun redactLine(line: String): String {
        var out = headerSecret.replace(line) { m -> "${m.groupValues[1]}: $REDACTED" }
        out = poToken.replace(out) { m -> "${m.groupValues[1]}=$REDACTED" }
        out = cookiePair.replace(out) { m -> "${m.groupValues[1]}=$REDACTED" }
        out = netscapeCookieLine.replace(out) { m -> m.groupValues[1] + REDACTED }
        out = url.replace(out) { m ->
            val raw = m.value
            val trailing = raw.takeLastWhile { it == ')' || it == ',' || it == '.' || it == ']' }
            redactUrl(raw.dropLast(trailing.length)) + trailing
        }
        return out
    }

    /**
     * Keeps scheme, host and path; replaces the query with `?<redacted>`.
     *
     * A page URL may keep its `v=` parameter so a log reader can still identify the video; every other parameter is
     * dropped because media URLs carry expiring signatures and PO tokens there.
     */
    fun redactUrl(rawUrl: String, keepVideoId: Boolean = true): String {
        val schemeEnd = rawUrl.indexOf("://")
        if (schemeEnd <= 0) return rawUrl
        val queryStart = rawUrl.indexOf('?')
        val fragmentStart = rawUrl.indexOf('#')
        val pathEnd = listOfNotNull(
            queryStart.takeIf { it >= 0 },
            fragmentStart.takeIf { it >= 0 },
        ).minOrNull() ?: rawUrl.length
        val base = rawUrl.substring(0, pathEnd)
        if (queryStart < 0) return base
        val queryEnd = fragmentStart.takeIf { it > queryStart } ?: rawUrl.length
        val query = rawUrl.substring(queryStart + 1, queryEnd)
        val kept = if (keepVideoId) {
            query.split('&')
                .filter { it.startsWith("v=") && it.length in 3..40 }
                .joinToString("&")
        } else {
            ""
        }
        val suffix = rawUrl.substring(queryEnd)
        return if (kept.isEmpty()) "$base?$REDACTED$suffix" else "$base?$kept&$REDACTED$suffix"
    }
}
