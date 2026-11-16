package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.*
import org.junit.Test

class AudioPresentationTest {
    private fun choice(id: String, ext: String = "m4a", codec: String = "mp4a.40.2") =
        AudioChoice(id, ext, codec, null)

    @Test fun twentyTwoVariantsBecomeTwoSourceBackedQuickChoices() {
        val choices = (0..10).map { choice("a$it") } + (0..10).map { choice("o$it", "webm", "opus") }
        val visible = AudioPresentation.quickChoices(choices)
        assertEquals(2, visible.size)
        assertTrue(choices.containsAll(visible))
        assertEquals(listOf("M4A · AAC", "WebM · Opus"), visible.map { it.label })
    }

    @Test fun quickChoicesAreBoundedAndKeepTheExplicitSelectionVisible() {
        val choices = listOf(choice("a"), choice("b", "webm", "opus"), choice("c", "ogg", "vorbis"),
            choice("d", "flac", "flac"), choice("selected", "m4a"))
        assertEquals(3, AudioPresentation.quickChoices(choices).size)
        assertEquals(4, AudioPresentation.quickChoices(choices, "selected").size)
        assertTrue(AudioPresentation.quickChoices(choices, "selected").any { it.formatId == "selected" })
        assertFalse(AudioPresentation.quickChoices(choices, "missing").any { it.formatId == "missing" })
    }

    @Test fun noInventedBestQualityOrSizeRankingAndNoDuplicateSelectedRow() {
        val choices = listOf(choice("first"), choice("second").copy(approxBytes = 99_000_000))
        assertEquals(listOf(choices.first()), AudioPresentation.quickChoices(choices, "first"))
        assertEquals(listOf(choices.first(), choices.last()), AudioPresentation.quickChoices(choices, "second"))
        assertTrue(AudioPresentation.quickChoices(emptyList()).isEmpty())
    }
}
