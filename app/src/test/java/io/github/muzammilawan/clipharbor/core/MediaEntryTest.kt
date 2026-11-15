package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEntryTest {
    @Test fun acceptsHttpsWithoutChangingSignedQuery() {
        val url = "HTTPS://example.invalid/watch?v=x&sig=a%2Fb#part"
        val input = MediaEntry.pasted("  $url  ") as MediaEntry.Input.Link
        assertEquals(url, input.url)
        assertFalse(input.toString().contains("sig"))
    }

    @Test fun rejectsUnsafeOrMalformedPastedInput() {
        listOf("", "http://example.invalid/a", "file:///a", "javascript:alert(1)",
            "https:///a", "https://user:password@example.invalid/a", "https://example.invalid:0/a",
            "https://example.invalid:65536/a", "https://example.invalid/a --exec bad",
            "https://example.invalid/a\nb", "https://example.invalid/a\\b", "https://example.invalid/%zz",
            "https://example.invalid/a https://example.invalid/b", "x".repeat(8193),
        ).forEach { assertTrue(it.take(50), MediaEntry.pasted(it) is MediaEntry.Input.Invalid) }
    }

    @Test fun sharedTitleAndLinkAreAcceptedButNotOptions() {
        val input = MediaEntry.shared("A shared title\n<https://example.invalid/watch?v=x>\n--exec ignored")
        assertEquals("https://example.invalid/watch?v=x", (input as MediaEntry.Input.Link).url)
    }

    @Test fun rejectsAmbiguousUnsupportedAndOversizedShares() {
        listOf("No link", "http://example.invalid/a", "https://example.invalid/a https://example.invalid/b",
            "https://example.invalid/a http://example.invalid/b", "https://example.invalid/a\u0000",
            "https://example.invalid/a" + "x".repeat(8192),
        ).forEach { assertTrue(MediaEntry.shared(it) is MediaEntry.Input.Invalid) }
    }

    @Test fun onlyOffersReportedPositiveHeightsWithAnAudioPath() {
        val formats = listOf(
            FormatInfo("v1", vcodec = "avc1", height = 1080),
            FormatInfo("v2", vcodec = "vp9", height = 1080),
            FormatInfo("mux", vcodec = "avc1", acodec = "mp4a", height = 360),
            FormatInfo("bad", vcodec = "avc1", height = -1),
            FormatInfo("unknown", vcodec = "avc1"),
        )
        assertEquals(listOf(360), MediaEntry.videoHeights(formats))
        assertEquals(listOf(1080, 360), MediaEntry.videoHeights(formats + FormatInfo("audio", acodec = "mp4a")))
        assertEquals(emptyList<Int>(), MediaEntry.videoHeights(listOf(FormatInfo("audio", acodec = "mp4a"))))
    }

    @Test fun defaultIsBestReportedAtOrBelow1080OtherwiseSmallest() {
        assertEquals(1080, MediaEntry.defaultHeight(listOf(2160, 1080, 720)))
        assertEquals(720, MediaEntry.defaultHeight(listOf(720, 360)))
        assertEquals(1440, MediaEntry.defaultHeight(listOf(2160, 1440)))
        assertEquals(null, MediaEntry.defaultHeight(emptyList()))
    }
}
