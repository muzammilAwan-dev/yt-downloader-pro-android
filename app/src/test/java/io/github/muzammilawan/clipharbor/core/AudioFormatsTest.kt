package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.*
import org.junit.Test

class AudioFormatsTest {
    private val aac = FormatInfo("140", "m4a", "none", "mp4a.40.2", approxBytes = 7_900_000)
    private val opus = FormatInfo("251", "webm", "none", "opus")

    @Test fun choicesAreOnlyReportedOriginalAudioWithHonestMetadata() {
        val choices = AudioFormats.choices(listOf(opus, aac, FormatInfo("v", "mp4", "avc1", "mp4a", 1280, 720)))
        assertEquals(listOf("140", "251"), choices.map { it.formatId })
        assertEquals("M4A · AAC", choices.first().label)
        assertTrue(choices.first().detail.contains("7.9 MB"))
        assertEquals("WebM · Opus", choices.last().label)
        assertFalse(choices.last().detail.contains("MB"))
        assertTrue(choices.none { it.label.contains("MP3") })
    }

    @Test fun unknownCodecsContainersUnsafeAndAmbiguousIdsFailClosed() {
        val bad = listOf(aac.copy(vcodec = null), aac.copy(acodec = null), aac.copy(acodec = "none"),
            aac.copy(ext = "exe"), aac.copy(formatId = "140']+b"), aac.copy(formatId = "x".repeat(129)))
        bad.forEach { assertTrue(AudioFormats.choices(listOf(it)).isEmpty()) }
        assertTrue(AudioFormats.choices(listOf(aac, aac.copy(vcodec = "avc1"))).isEmpty())
        assertTrue(AudioFormats.choices((0..512).map { aac.copy(formatId = "a$it") }).isEmpty())
        assertTrue(AudioFormats.choices(emptyList()).isEmpty())
    }

    @Test fun exactTypedPlanHasNoVideoOrUnboundedFallbackAndPreservesExtension() {
        val plan = AudioFormats.plan(listOf(aac, opus), "140")!!
        assertEquals("ba[format_id='140']", AudioFormats.selector(plan))
        assertEquals("audio/mp4", plan.mimeType)
        assertTrue(AudioFormats.outputTemplate().contains("_audio"))
        assertTrue(AudioFormats.outputTemplate().endsWith(".%(ext)s"))
        assertFalse(plan.toString().contains("140"))
        assertNull(AudioFormats.plan(listOf(aac), "251"))
        assertNull(AudioFormats.plan(listOf(aac), "--exec=bad"))
    }

    @Test fun audioContainersHaveAudioMimeTypesIncludingWebm() {
        val expected = mapOf("m4a" to "audio/mp4", "webm" to "audio/webm", "mp3" to "audio/mpeg",
            "opus" to "audio/ogg", "ogg" to "audio/ogg", "flac" to "audio/flac", "wav" to "audio/wav")
        expected.forEach { (ext, mime) ->
            val plan = AudioFormats.plan(listOf(aac.copy(ext = ext)), "140")!!
            assertEquals(mime, plan.mimeType)
            assertEquals(ext, plan.extension)
        }
    }

    @Test fun sizesAndCodecLabelsAreBoundedAndNeverEchoUnknownSourceText() {
        val choice = AudioFormats.choices(listOf(aac.copy(acodec = "private-source-text", approxBytes = -1))).single()
        assertEquals("M4A · audio", choice.label)
        assertFalse(choice.detail.contains("MB"))
        assertFalse(choice.toString().contains("private-source-text"))
    }
}
