package io.github.muzammilawan.clipharbor.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {
    private val secret = "S3CR3T-VALUE-9f8e7d"

    @Test
    fun cookieHeaderValueIsRemoved() {
        val line = "Cookie: SAPISID=$secret; HSID=another"
        val out = LogRedactor.redactLine(line)
        assertFalse(out.contains(secret))
        assertFalse(out.contains("another"))
        assertTrue(out.contains("Cookie: <redacted>"))
    }

    @Test
    fun authorizationHeaderIsRemoved() {
        val out = LogRedactor.redactLine("Authorization: Bearer ya29.$secret")
        assertFalse(out.contains(secret))
        assertTrue(out.contains("<redacted>"))
    }

    @Test
    fun signedMediaUrlKeepsHostAndPathOnly() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1763&sig=$secret&mh=xy"
        val out = LogRedactor.redactLine(url)
        assertTrue(out.contains("rr3---sn-abc.googlevideo.com/videoplayback"))
        assertTrue(out.contains("<redacted>"))
        assertFalse(out.contains(secret))
        assertFalse(out.contains("expire=1763"))
    }

    @Test
    fun watchUrlKeepsVideoIdButDropsPlaylistParams() {
        val out = LogRedactor.redactUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL$secret&index=2")
        assertTrue(out.contains("v=dQw4w9WgXcQ"))
        assertFalse(out.contains(secret))
        assertFalse(out.contains("index=2"))
    }

    @Test
    fun poTokenIsRemoved() {
        val out = LogRedactor.redactLine("using po_token=$secret for gvs")
        assertFalse(out.contains(secret))
    }

    @Test
    fun netscapeCookieFileLineLosesItsValue() {
        val line = "youtube.com\tTRUE\t/\tTRUE\t1893456000\tSAPISID\t$secret"
        val out = LogRedactor.redactLine(line)
        assertFalse(out.contains(secret))
        assertTrue(out.startsWith("youtube.com\tTRUE\t/\tTRUE\t1893456000\tSAPISID\t"))
    }

    @Test
    fun ordinaryLogLineIsUnchanged() {
        val line = "2026-11-15T04:11:34.550Z I task=17 stage=RESOLVING event=ok formats=26"
        assertTrue(LogRedactor.redactLine(line) == line)
    }

    @Test
    fun trailingPunctuationAfterUrlSurvives() {
        val out = LogRedactor.redactLine("see https://example.invalid/v.mp4?a=$secret.")
        assertTrue(out.endsWith("."))
        assertFalse(out.contains(secret))
    }
}
