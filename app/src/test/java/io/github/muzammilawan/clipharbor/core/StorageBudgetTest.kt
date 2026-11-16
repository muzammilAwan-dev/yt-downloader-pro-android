package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.*
import org.junit.Test

class StorageBudgetTest {
    @Test fun mergeAndPublicationCopyAreBudgetedConservativelyWithReserve() {
        val size = 1_000_000L
        val space = StorageSpace(100_000_000, 200_000_000)
        assertEquals(StorageBudget.RESERVE + 3 * size, StorageBudget.download(space, size, true).requiredBytes)
        assertEquals(StorageBudget.RESERVE + 2 * size, StorageBudget.download(space, size, false).requiredBytes)
        assertTrue(StorageBudget.download(space, size, true).allowed)
        assertFalse(StorageBudget.download(StorageSpace(0, 200_000_000), size, true).allowed)
        assertFalse(StorageBudget.download(StorageSpace(200_000_000, 0), size, true).allowed)
    }

    @Test fun unknownSizeIsNotInventedAndOverflowCannotAdmitHugeInputs() {
        val report = StorageBudget.download(StorageSpace(Long.MAX_VALUE, Long.MAX_VALUE), null, true)
        assertTrue(report.allowed)
        assertNull(report.estimatedBytes)
        assertTrue(report.summary.contains("unknown"))
        assertFalse(StorageBudget.download(StorageSpace(Long.MAX_VALUE - 1, Long.MAX_VALUE), Long.MAX_VALUE, true).allowed)
        assertFalse(StorageBudget.download(StorageSpace(-1, 100_000_000), 1, true).allowed)
    }

    @Test fun publicationUsesActualBytesAndChecksDestinationAgain() {
        assertTrue(StorageBudget.publication(StorageSpace(0, StorageBudget.RESERVE + 100), 100).allowed)
        assertFalse(StorageBudget.publication(StorageSpace(Long.MAX_VALUE, StorageBudget.RESERVE + 99), 100).allowed)
    }

    @Test fun reportedEstimateIncludesAudioAndUnknownCandidateSizeRemainsUnknown() {
        val video = FormatInfo("137", "mp4", "avc1", "none", 1920, 1080, approxBytes = 10L)
        val audio = FormatInfo("140", "m4a", "none", "mp4a", approxBytes = 2L)
        val formats = listOf(video, audio)
        val plan = VideoQualities.plan(formats, VideoQuality.P1080)!!
        assertEquals(12L, StorageBudget.estimate(formats, plan))
        assertNull(StorageBudget.estimate(listOf(video, audio.copy(approxBytes = null)), plan))
        assertNull(StorageBudget.estimate(formats + video.copy(formatId = "extra", approxBytes = null),
            VideoQualities.plan(formats + video.copy(formatId = "extra", approxBytes = null), VideoQuality.P1080)!!))
    }
}
