package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.*
import org.junit.Test

class VideoQualityTest {
    private fun video(id: String, width: Int?, height: Int?, audio: String = "none") =
        FormatInfo(id, "mp4", "avc1", audio, width, height)
    private val audio = FormatInfo("140", "m4a", "none", "mp4a")

    @Test fun standardLabelsDistinguishQhdAnd4k() {
        assertEquals(listOf(144, 240, 360, 480, 720, 1080, 1440, 2160), VideoQuality.entries.map { it.pixels })
        assertTrue(VideoQuality.P1440.label.contains("QHD"))
        assertFalse(VideoQuality.P1440.label.contains("4K"))
        assertTrue(VideoQuality.P2160.label.contains("4K"))
    }

    @Test fun portraitAndLandscapeHaveIdenticalQualityTiers() {
        for (quality in VideoQuality.entries) {
            val landscape = VideoQualities.choices(listOf(video("v", quality.pixels * 2, quality.pixels), audio))
            val portrait = VideoQualities.choices(listOf(video("v", quality.pixels, quality.pixels * 2), audio))
            assertEquals(listOf(quality), landscape.filter { it.available }.map { it.quality })
            assertEquals(listOf(quality), portrait.filter { it.available }.map { it.quality })
        }
    }

    @Test fun nonstandardDimensionsAreShownHonestlyWithoutDuplicateHigherTiers() {
        val choices = VideoQualities.choices(listOf(video("v", 760, 1350), audio))
        assertEquals(listOf(VideoQuality.P1080), choices.filter { it.available }.map { it.quality })
        assertTrue(choices.single { it.available }.detail.contains("760 × 1350"))
        assertFalse(choices.single { it.quality == VideoQuality.P720 }.available)
        assertEquals(VideoQuality.P1080, VideoQualities.default(choices))
        assertNull(VideoQualities.plan(listOf(video("v", 760, 1350), audio), VideoQuality.P720))
    }

    @Test fun missingDimensionsUnsafeIdsAndVideoWithoutAudioAreNotPromises() {
        val bad = listOf(video("unknown", null, 720), video("zero", 0, 720), video("bad']+b", 1280, 720))
        assertTrue(VideoQualities.choices(bad + audio).none { it.available })
        assertTrue(VideoQualities.choices(listOf(video("orphan", 1280, 720))).none { it.available })
        assertTrue(VideoQualities.choices(listOf(video("muxed", 1280, 720, "mp4a"))).any { it.available })
        assertTrue(VideoQualities.choices(emptyList()).none { it.available })
    }

    @Test fun planKeepsHighestResolutionSeparateFromCappedFallbacks() {
        val formats = listOf(video("low", 1280, 720), video("high.1", 1080, 1920),
            video("over", 1440, 2560), audio)
        val plan = VideoQualities.plan(formats, VideoQuality.P1080)!!
        assertEquals(listOf("high.1"), plan.bestIds)
        assertEquals(listOf("high.1", "low"), plan.cappedIds)
        val branches = FormatPlanner.selector(plan).split('/')
        assertEquals(6, branches.size)
        branches.forEach { assertTrue(it.contains("format_id~=")); assertFalse(it.contains("over")) }
        assertTrue(branches.first().contains("high\\.1"))
        assertFalse(branches.first().contains("low"))
        assertTrue(branches.first().contains("vcodec^=avc1"))
        assertTrue(branches.last().contains("low"))
    }

    @Test fun defaultDoesNotChoose4kUnlessNoLowerTierIsReported() {
        val formats = listOf(video("hd", 1280, 720), video("4k", 3840, 2160), audio)
        assertEquals(VideoQuality.P720, VideoQualities.default(VideoQualities.choices(formats)))
        assertEquals(VideoQuality.P2160, VideoQualities.default(VideoQualities.choices(formats.drop(1))))
    }

    @Test fun outputNamingPreservesRealExtensionAndLabelsTheCeiling() {
        VideoQuality.entries.forEach {
            val template = FormatPlanner.outputTemplate(it)
            assertTrue(template.contains("_up-to-${it.pixels}p"))
            assertTrue(template.endsWith(".%(ext)s"))
            assertTrue(template.contains("%(title).150B"))
        }
    }
}
