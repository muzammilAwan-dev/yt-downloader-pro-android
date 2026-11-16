package io.github.muzammilawan.clipharbor.core

import java.io.File
import java.util.Properties
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

    @Test fun visiblePickerStopsAtReported1440pAndOmitsMissingTiers() {
        val formats = listOf(video("qhd", 2560, 1440), video("fhd", 1920, 1080),
            video("hd", 1280, 720), audio)
        val visible = VideoQualities.visibleChoices(VideoQualities.choices(formats))
        assertEquals(listOf(VideoQuality.P1440, VideoQuality.P1080, VideoQuality.P720), visible.map { it.quality })
        assertTrue(visible.all { it.available })
        assertEquals(listOf("1440p · QHD", "1080p · Full HD", "720p · HD"), visible.map { it.label })
        assertTrue(visible.none { it.label.contains("4K") })
        assertNull(VideoQualities.plan(formats, VideoQuality.P2160))
    }

    @Test fun visiblePickerDoesNotInventOtherChoicesForOneLowStream() {
        val formats = listOf(video("low", 640, 360, "mp4a"))
        val visible = VideoQualities.visibleChoices(VideoQualities.choices(formats))
        assertEquals(listOf(VideoQuality.P360), visible.map { it.quality })
        assertEquals("360p", visible.single().label)
        assertEquals(VideoQuality.P360, VideoQualities.default(visible))
    }

    @Test fun real4kIsStillVisibleInEitherOrientation() {
        for ((width, height) in listOf(3840 to 2160, 2160 to 3840)) {
            val visible = VideoQualities.visibleChoices(VideoQualities.choices(listOf(video("4k", width, height), audio)))
            assertEquals(listOf(VideoQuality.P2160), visible.map { it.quality })
            assertEquals("2160p · 4K", visible.single().label)
        }
    }

    @Test fun nonstandardPickerLabelsDescribeSourceNotThePlanningCeiling() {
        for ((pixels, ceiling) in listOf(760 to VideoQuality.P1080, 1600 to VideoQuality.P2160)) {
            for ((width, height) in listOf(pixels * 2 to pixels, pixels to pixels * 2)) {
                val formats = listOf(video("v", width, height), audio)
                val visible = VideoQualities.visibleChoices(VideoQualities.choices(formats))
                assertEquals(listOf(ceiling), visible.map { it.quality })
                assertEquals("${pixels}p", visible.single().label)
                assertTrue(visible.single().detail.contains("$width × $height"))
                assertEquals(listOf("v"), VideoQualities.plan(formats, ceiling)!!.bestIds)
            }
        }
    }

    @Test fun audioOnlyAndUnsubstantiatedVideoDoNotCreateVisibleVideoChoices() {
        for (formats in listOf(emptyList(), listOf(audio), listOf(video("unknown", null, 720), audio),
            listOf(video("bad']+b", 1280, 720), audio), listOf(video("orphan", 1280, 720)))) {
            val visible = VideoQualities.visibleChoices(VideoQualities.choices(formats))
            assertTrue(visible.isEmpty())
            assertNull(VideoQualities.default(visible))
        }
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
        assertEquals(8, branches.size)
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

    @Test fun ambiguousIdsAndOversizedCandidateListsFailClosed() {
        assertTrue(VideoQualities.choices(listOf(video("same", 1280, 720), video("same", 3840, 2160), audio))
            .none { it.available })
        assertTrue(VideoQualities.choices((0..512).map { video("v$it", 1280, 720) } + audio).none { it.available })
    }

    /** Optional Python checker consumes these actual Kotlin-generated selectors; CI needs no Python/network. */
    @Test fun writeOfflineNativeSelectorMatrixFromActualPlanner() {
        val cases = Properties()
        var count = 0
            VideoQuality.entries.forEach { quality ->
                listOf(false, true).forEach { portrait ->
                    fun v(id: String, pixels: Int, codec: String = "avc1", acodec: String = "none") =
                        FormatInfo(id, if (codec == "avc1") "mp4" else "webm", codec, acodec,
                            if (portrait) pixels else pixels * 2, if (portrait) pixels * 2 else pixels)
                    val h264 = v("top.h264", quality.pixels)
                    val av1 = v("top.av1", quality.pixels, "av01")
                    val low = v("low", 100)
                    val over = v("over", quality.pixels * 2)
                    val aac = FormatInfo("aac", "m4a", "none", "mp4a")
                    val opus = FormatInfo("opus", "webm", "none", "opus")
                    val base = listOf(h264, av1, low, over, aac, opus)
                    fun case(name: String, source: List<FormatInfo>, offered: List<FormatInfo>, expected: String?) {
                        val plan = VideoQualities.plan(source, quality)!!
                        val selector = FormatPlanner.selector(plan)
                        assertTrue(plan.cappedIds.none { it == "over" })
                        val prefix = "case.${count++}."
                        cases.setProperty(prefix + "name", "${quality.pixels}-${if (portrait) "portrait" else "landscape"}-$name")
                        cases.setProperty(prefix + "cap", quality.pixels.toString())
                        cases.setProperty(prefix + "selector", selector)
                        cases.setProperty(prefix + "expected", expected.orEmpty())
                        cases.setProperty(prefix + "formats", offered.joinToString("\n") { f ->
                            listOf(f.formatId, f.ext, f.vcodec, f.acodec, f.width ?: "NA", f.height ?: "NA").joinToString("\t")
                        })
                    }
                    case("compatible", base, base, "top.h264+aac")
                    case("resolution-before-codec", base - h264, base - h264, "top.av1+aac")
                    case("opus-fallback", base - h264 - aac, base - h264 - aac, "top.av1+opus")
                    val muxed = v("muxed", quality.pixels, acodec = "mp4a")
                    case("muxed-only", listOf(muxed, over), listOf(muxed, over), "muxed")
                    case("lower-fallback", base, listOf(low, over, aac, opus), "low+aac")
                    case("never-unbounded", base, listOf(over, aac), null)
                }
            }
        assertEquals(96, count)
        cases.setProperty("count", count.toString())
        val module = sequenceOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
        File(module, "build/reports/quality-selectors.xml").apply {
            parentFile?.mkdirs()
            outputStream().use { cases.storeToXML(it, "Synthetic Kotlin planner fixtures", "UTF-8") }
        }
    }
}
