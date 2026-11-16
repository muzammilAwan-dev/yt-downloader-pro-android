package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties

class ExactVideoFormatsTest {
    private val video = FormatInfo("137", "mp4", "avc1.640028", "none", 1920, 1080, 30, 10L)
    private val audio = FormatInfo("140", "m4a", "none", "mp4a.40.2", approxBytes = 2L)
    private val muxed = FormatInfo("18", "mp4", "avc1", "mp4a", 640, 360)

    @Test fun choicesAreBoundedUniqueSourceBackedAndTruthfullyDistinguishMuxedVersusSeparateAudio() {
        val choices = ExactVideoFormats.choices(listOf(video, audio, muxed))
        assertEquals(listOf("137", "18"), choices.map { it.formatId })
        assertTrue(choices.first().label.contains("1920 × 1080"))
        assertTrue(choices.first().detail.contains("30 fps"))
        assertTrue(choices.first().detail.contains("audio selected automatically"))
        assertTrue(choices.last().detail.contains("source audio included"))
        assertEquals(12L, ExactVideoFormats.plan(listOf(video, audio), "137")!!.estimatedBytes)
        assertEquals("mkv", ExactVideoFormats.plan(listOf(video, audio), "137")!!.mergeContainer)
    }

    @Test fun unsafeAmbiguousAndMissingAudioOrDimensionsFailClosed() {
        assertTrue(ExactVideoFormats.choices(listOf(video)).isEmpty())
        assertTrue(ExactVideoFormats.choices(listOf(video, video, audio)).isEmpty())
        assertTrue(ExactVideoFormats.choices(listOf(video.copy(formatId = "x']+b"), audio)).isEmpty())
        assertTrue(ExactVideoFormats.choices(listOf(video.copy(width = null), audio)).isEmpty())
        assertTrue(ExactVideoFormats.choices(listOf(video.copy(ext = "exe"), audio)).isEmpty())
        assertTrue(ExactVideoFormats.choices((0..512).map { muxed.copy(formatId = "v$it") }).isEmpty())
        assertNull(ExactVideoFormats.plan(listOf(muxed), "missing"))
    }

    @Test fun exactSelectorNeverFallsBackToAnotherVideoAndMuxedDoesNotAddAudio() {
        val plan = ExactVideoFormats.plan(listOf(video, audio), "137")!!
        val selector = ExactVideoFormats.selector(plan)
        assertTrue(selector.contains("bv[format_id='137']+ba"))
        assertTrue(selector.contains("140"))
        assertFalse(selector.contains("height<="))
        assertEquals("b[format_id='18']", ExactVideoFormats.selector(ExactVideoFormats.plan(listOf(muxed), "18")!!))
        assertTrue(ExactVideoFormats.outputTemplate(plan).contains("_format-137"))
    }

    @Test fun evidenceMustMatchExactVideoDimensionsAndReportedAudioCandidates() {
        val plan = ExactVideoFormats.plan(listOf(video, audio), "137")!!
        assertTrue(ExactVideoFormats.matches(plan, listOf("137", "140"), 1920, 1080))
        for (ids in listOf(listOf("137"), listOf("other", "140"), listOf("137", "unlisted"), listOf("140", "137")))
            assertFalse(ExactVideoFormats.matches(plan, ids, 1920, 1080))
        assertFalse(ExactVideoFormats.matches(plan, listOf("137", "140"), 1280, 720))
        assertFalse(ExactVideoFormats.matches(plan, listOf("137", "140"), null, 1080))
        assertTrue(ExactVideoFormats.matches(ExactVideoFormats.plan(listOf(muxed), "18")!!, listOf("18"), 640, 360))
    }

    @Test fun exportActualExactSelectorsForOfflinePinnedEngineVerification() {
        val fixtures = Properties()
        var count = 0
        for (portrait in listOf(false, true)) for (codec in listOf("avc1", "vp9")) {
            val video = this.video.copy(formatId = "exact.video-1", vcodec = codec,
                width = if (portrait) 1080 else 1920, height = if (portrait) 1920 else 1080)
            val opus = audio.copy(formatId = "audio.opus-1", ext = "webm", acodec = "opus")
            val other = video.copy(formatId = "other.video")
            val source = listOf(video, audio, opus, other)
            val plan = ExactVideoFormats.plan(source, video.formatId)!!
            fun fixture(formats: List<FormatInfo>, expected: String, chosen: ExactVideoPlan = plan) {
                val prefix = "case.${count++}."
                fixtures.setProperty(prefix + "selector", ExactVideoFormats.selector(chosen))
                fixtures.setProperty(prefix + "expected", expected)
                fixtures.setProperty(prefix + "formats", formats.joinToString("\n") { f ->
                    listOf(f.formatId, f.ext, f.vcodec, f.acodec, f.width ?: "NA", f.height ?: "NA").joinToString("\t")
                })
            }
            fixture(source, "exact.video-1+140")
            fixture(source - audio, "exact.video-1+audio.opus-1")
            fixture(source - video, "")
            fixture(listOf(video, other), "")
            fixture(listOf(muxed, other), "18", ExactVideoFormats.plan(listOf(muxed), "18")!!)
        }
        assertEquals(20, count)
        fixtures.setProperty("count", count.toString())
        val module = sequenceOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
        File(module, "build/reports/exact-video-selectors.xml").apply {
            parentFile?.mkdirs(); outputStream().use { fixtures.storeToXML(it, "Actual exact-video planner fixtures", "UTF-8") }
        }
    }
}
