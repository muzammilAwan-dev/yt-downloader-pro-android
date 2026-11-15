package io.github.muzammilawan.clipharbor.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadEvidenceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun parsesAdaptiveSelectionAndActualHeight() {
        val evidence = DownloadEvidence()
        evidence.accept("[info] synthetic: Downloading 1 format(s): 137+140")
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}137+140\t1080")
        assertEquals(listOf("137", "140"), evidence.formatIds())
        assertEquals(1080, evidence.height())
    }

    @Test
    fun laterFallbackReplacesSelectionAndDoesNotReuseHeight() {
        val evidence = DownloadEvidence()
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}137+140\t1080")
        evidence.accept("[info] synthetic: Downloading 1 format(s): 18")
        assertEquals(listOf("18"), evidence.formatIds())
        assertEquals(null, evidence.height())
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}18\t360")
        assertEquals(360, evidence.height())
    }

    @Test
    fun unknownAudioHeightIsNotInvented() {
        val evidence = DownloadEvidence()
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}140\tNA")
        assertEquals(listOf("140"), evidence.formatIds())
        assertEquals(null, evidence.height())
    }

    @Test
    fun malformedOrUnrelatedSelectionDoesNotBecomeEvidence() {
        val evidence = DownloadEvidence()
        listOf(
            "[debug] Downloading 1 format(s): 137+140",
            "${DownloadEvidence.SELECTION_MARKER}https://example.invalid/?token=secret\t1080",
            "${DownloadEvidence.SELECTION_MARKER}137+\t1080",
            "${DownloadEvidence.SELECTION_MARKER}137\t1080\textra",
            "${DownloadEvidence.SELECTION_MARKER}${"a".repeat(257)}\t1080",
        ).forEach(evidence::accept)
        assertTrue(evidence.formatIds().isEmpty())
        assertEquals(null, evidence.height())
    }

    @Test
    fun explicitFinalPathSupportsSpacesUnicodeAndOldModificationTime() {
        val directory = temporary.newFolder("task")
        val output = File(directory, "sample café [id].mp4").apply {
            writeText("fixture")
            setLastModified(1L)
        }
        val evidence = DownloadEvidence()
        evidence.accept(DownloadEvidence.FILE_MARKER + output.absolutePath + "\r")
        evidence.accept(DownloadEvidence.FILE_MARKER + output.absolutePath)
        assertEquals(listOf(output.canonicalFile), evidence.outputFiles(directory))
    }

    @Test
    fun mergerAndFreshFilesAreNotProofOfFinalOutput() {
        val directory = temporary.newFolder("task")
        val output = File(directory, "unrelated.mp4").apply { writeText("fixture") }
        val evidence = DownloadEvidence()
        evidence.accept("[Merger] Merging formats into \"${output.absolutePath}\"")
        evidence.accept(output.absolutePath)
        assertTrue(evidence.outputFiles(directory).isEmpty())
    }

    @Test
    fun rejectsSiblingTraversalRelativeAndMissingPaths() {
        val directory = temporary.newFolder("task")
        val outside = File(temporary.newFolder("task-other"), "other.mp4").apply { writeText("fixture") }
        val evidence = DownloadEvidence()
        listOf(
            outside.absolutePath,
            File(directory, "../task-other/other.mp4").absolutePath,
            "relative.mp4",
            File(directory, "missing.mp4").absolutePath,
            directory.absolutePath,
        ).forEach { path ->
            evidence.accept(DownloadEvidence.FILE_MARKER + path)
            assertTrue(path, evidence.outputFiles(directory).isEmpty())
        }
    }

    @Test
    fun rejectsPartialMetadataAndEmptyFiles() {
        val directory = temporary.newFolder("task")
        val evidence = DownloadEvidence()
        listOf("sample.mp4.part", "sample.ytdl", "resolved.json", "empty.mp4").forEach { name ->
            val output = File(directory, name).apply { if (name == "empty.mp4") createNewFile() else writeText("fixture") }
            evidence.accept(DownloadEvidence.FILE_MARKER + output.absolutePath)
            assertTrue(name, evidence.outputFiles(directory).isEmpty())
        }
    }

    @Test
    fun laterFinalMarkerWinsInsteadOfPublishingPreviousFile() {
        val directory = temporary.newFolder("task")
        val output = File(directory, "first.mp4").apply { writeText("fixture") }
        val evidence = DownloadEvidence()
        evidence.accept(DownloadEvidence.FILE_MARKER + output.absolutePath)
        evidence.accept(DownloadEvidence.FILE_MARKER + File(directory, "second.mp4").absolutePath)
        assertTrue(evidence.outputFiles(directory).isEmpty())
    }

    @Test
    fun rejectsSymlinkEscapeWhenHostSupportsSymlinks() {
        val directory = temporary.newFolder("task")
        val outside = temporary.newFile("outside.mp4").apply { writeText("fixture") }
        val link = File(directory, "link.mp4")
        // Windows may require developer mode/admin for symlinks; other confinement cases always execute.
        org.junit.Assume.assumeTrue(runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess)
        val evidence = DownloadEvidence()
        evidence.accept(DownloadEvidence.FILE_MARKER + link.absolutePath)
        assertTrue(evidence.outputFiles(directory).isEmpty())
    }
}
