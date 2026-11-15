package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatPlannerTest {
    private val formats = listOf(
        FormatInfo("137", "mp4", "avc1.640028", "none", height = 1080),
        FormatInfo("248", "webm", "vp9", "none", height = 1080, note = "1080p"),
        FormatInfo("136", "mp4", "avc1.4d401f", "none", height = 720),
        FormatInfo("140", "m4a", "none", "mp4a.40.2"),
        FormatInfo("251", "webm", "none", "opus"),
        FormatInfo("18", "mp4", "avc1.42001E", "mp4a.40.2", height = 360),
    )

    @Test
    fun selectorPrefersH264AacCappedAtRequestedHeight() {
        val selector = FormatPlanner.selector(1080)
        assertTrue(selector.startsWith("bv*[height<=1080][vcodec^=avc1]+ba[acodec^=mp4a]"))
        assertTrue(selector.contains("/b[height<=1080]/b"))
    }

    @Test
    fun selectorRejectsNonPositiveHeight() {
        assertThrows(IllegalArgumentException::class.java) { FormatPlanner.selector(0) }
    }

    @Test
    fun summaryCountsStreamKindsAndHeights() {
        val summary = FormatPlanner.summary(formats)
        assertEquals(6, summary.total)
        assertEquals(3, summary.videoOnly)
        assertEquals(2, summary.audioOnly)
        assertEquals(1, summary.muxed)
        assertEquals(1080, summary.maxHeight)
        assertEquals(360, summary.maxHeightMuxed)
        assertTrue(summary.has1080p)
        assertFalse(summary.hdUnavailable)
    }

    @Test
    fun hdUnavailableWhenOnlyLowQualityIsOffered() {
        val lowOnly = listOf(FormatInfo("18", "mp4", "avc1", "mp4a", height = 360))
        val summary = FormatPlanner.summary(lowOnly)
        assertTrue(summary.hdUnavailable)
        assertFalse(summary.has1080p)
        assertTrue(FormatPlanner.hdRequestWillFallShort(summary, 1080))
    }

    @Test
    fun emptyFormatListIsNotTreatedAsWithheld() {
        val summary = FormatPlanner.summary(emptyList())
        assertFalse(summary.hdUnavailable)
        assertFalse(FormatPlanner.hdRequestWillFallShort(summary, 1080))
    }

    @Test
    fun describeProducesGreppableFields() {
        val text = FormatPlanner.describe(FormatPlanner.summary(formats))
        assertTrue(text.contains("formats=6"))
        assertTrue(text.contains("maxHeight=1080"))
        assertTrue(text.contains("hdUnavailable=false"))
    }

    @Test
    fun outputTemplateIsBoundedAndKeepsIdAndExtension() {
        val template = FormatPlanner.outputTemplate()
        assertTrue(template.contains("%(id)s"))
        assertTrue(template.contains(".%(ext)s"))
        assertTrue(template.contains("%(title).150B"))
    }
}
