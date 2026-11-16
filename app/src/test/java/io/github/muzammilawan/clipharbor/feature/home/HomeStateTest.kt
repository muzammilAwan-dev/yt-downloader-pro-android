package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.AudioChoice
import io.github.muzammilawan.clipharbor.core.QualityChoice
import io.github.muzammilawan.clipharbor.core.VideoQuality
import org.junit.Assert.*
import org.junit.Test

class HomeStateTest {
    private val video = QualityChoice(VideoQuality.P720, 1280, 720)
    private val audio = AudioChoice("140", "m4a", "mp4a.40.2", null)
    private val choosing = HomeState(phase = HomePhase.CHOOSING, engineReady = true,
        qualities = listOf(video, QualityChoice(VideoQuality.P2160)), audioChoices = listOf(audio))

    @Test fun validVideoAndAudioUseDistinctExplicitDownloadLabels() {
        val videoState = choosing.copy(selectedQuality = VideoQuality.P720)
        assertTrue(videoState.canDownload)
        assertEquals("Download video + audio", videoState.downloadLabel)
        val audioState = choosing.copy(selectedAudioFormatId = "140")
        assertTrue(audioState.canDownload)
        assertEquals("Download audio only", audioState.downloadLabel)
    }

    @Test fun absentUnknownUnavailableAndConflictingSelectionsCannotDownload() {
        assertFalse(choosing.canDownload)
        assertFalse(choosing.copy(selectedQuality = VideoQuality.P2160).canDownload)
        assertFalse(choosing.copy(selectedAudioFormatId = "251").canDownload)
        assertFalse(choosing.copy(selectedAudioFormatId = "140", selectedQuality = VideoQuality.P720).canDownload)
    }

    @Test fun noOtherPhaseCanEnableDownloadEvenWithRetainedChoices() {
        HomePhase.entries.filter { it != HomePhase.CHOOSING }.forEach { phase ->
            assertFalse(choosing.copy(phase = phase, selectedAudioFormatId = "140").canDownload)
            assertFalse(choosing.copy(phase = phase, selectedQuality = VideoQuality.P720).canDownload)
        }
        assertFalse(choosing.copy(engineReady = false, selectedAudioFormatId = "140").canDownload)
    }

    @Test fun originalAudioDoesNotRequireAnyVideoChoices() {
        assertTrue(choosing.copy(qualities = emptyList(), selectedAudioFormatId = "140").canDownload)
        assertFalse(choosing.copy(audioChoices = emptyList(), selectedAudioFormatId = "140").canDownload)
    }

    @Test fun labelsDoNotLeakUnknownCodecTextOrInventSizeBitrateOrConversion() {
        val original = audio.copy(codec = "PRIVATE_CODEC", approxBytes = null)
        assertEquals("M4A · audio", original.label)
        assertEquals("Original audio · format 140", original.detail)
        assertFalse(original.detail.contains("kbps"))
        assertFalse(original.detail.contains("MP3"))
        assertFalse(original.detail.contains("MB"))
    }
}
