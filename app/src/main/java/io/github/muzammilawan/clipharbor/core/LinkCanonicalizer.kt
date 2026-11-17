package io.github.muzammilawan.clipharbor.core

import java.net.URI

/**
 * Canonicalises public media links for the recognised hosts only.
 *
 * Rules:
 * - an unknown host is returned unchanged; arbitrary parameters are never stripped blindly;
 * - a URL carrying a signature-like parameter (signed/CDN URLs) is returned unchanged;
 * - only a short, host-specific list of share/tracking parameters is dropped;
 * - scheme and host are lowercased, the fragment is dropped, path and remaining query order are preserved.
 *
 * This is data cleanup, not authorisation: the HTTPS/user-info/port checks stay in [MediaEntry.pasted].
 */
object LinkCanonicalizer {
    private data class Rule(val host: String, val drop: Set<String>)

    private val signedParameters = setOf(
        "sig", "signature", "expires", "expire", "token", "hdnts", "policy", "key-pair-id",
        "x-amz-signature", "x-amz-credential", "x-amz-security-token",
    )

    private val rules = listOf(
        Rule("youtube.com", setOf("si", "feature", "pp", "ab_channel", "fbclid", "gclid")),
        Rule("youtu.be", setOf("si", "feature", "pp", "ab_channel", "fbclid", "gclid")),
        Rule("instagram.com", setOf("igsh", "igshid", "fbclid", "gclid")),
        Rule("facebook.com", setOf("fbclid", "mibextid", "ref", "refid", "__tn__", "__cft__", "_rdr",
            "extid", "hc_ref", "fref", "gclid")),
        Rule("fb.watch", setOf("fbclid", "mibextid", "ref", "refid", "gclid")),
        Rule("x.com", setOf("s", "t", "ref_src", "ref_url", "fbclid", "gclid")),
        Rule("twitter.com", setOf("s", "t", "ref_src", "ref_url", "fbclid", "gclid")),
        Rule("tiktok.com", setOf("is_from_webapp", "sender_device", "web_id", "_r", "_t", "share_app_id",
            "share_item_id", "social_sharing", "source", "u_code", "timestamp", "refer", "fbclid", "gclid")),
        Rule("reddit.com", setOf("share_id", "rdt", "fbclid", "gclid")),
        Rule("redd.it", setOf("share_id", "rdt", "fbclid", "gclid")),
        Rule("twitch.tv", setOf("tt_content", "tt_medium", "fbclid", "gclid")),
        Rule("vimeo.com", setOf("share", "fbclid", "gclid")),
        Rule("soundcloud.com", setOf("si", "fbclid", "gclid")),
    )

    /** Returns [url] unchanged when the host is unknown, the URL is signed, or parsing fails. */
    fun canonicalize(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        val rule = rules.firstOrNull { host == it.host || host.endsWith("." + it.host) } ?: return url
        val pairs = queryPairs(uri.rawQuery)
        if (pairs.any { it.key.lowercase() in signedParameters }) return url
        val kept = pairs.filterNot { pair ->
            val key = pair.key.lowercase()
            key in rule.drop || key.startsWith("utm_")
        }
        return rebuild(uri, host, kept)
    }

    private data class Pair(val key: String, val value: String, val hadEquals: Boolean)

    private fun queryPairs(rawQuery: String?): List<Pair> {
        if (rawQuery.isNullOrEmpty()) return emptyList()
        return rawQuery.split('&').filter { it.isNotEmpty() }.map { part ->
            val index = part.indexOf('=')
            if (index < 0) Pair(part, "", false)
            else Pair(part.substring(0, index), part.substring(index + 1), true)
        }
    }

    private fun rebuild(uri: URI, host: String, pairs: List<Pair>): String = buildString {
        append(uri.scheme?.lowercase() ?: "https").append("://").append(host)
        if (uri.port != -1) append(':').append(uri.port)
        append(uri.rawPath.orEmpty())
        if (pairs.isNotEmpty()) {
            append('?')
            pairs.forEachIndexed { index, pair ->
                if (index > 0) append('&')
                append(pair.key)
                if (pair.hadEquals) append('=').append(pair.value)
            }
        }
    }
}
