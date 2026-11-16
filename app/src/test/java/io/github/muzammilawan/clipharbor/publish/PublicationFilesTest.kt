package io.github.muzammilawan.clipharbor.publish

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublicationFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun pendingTransactionReturnsOnlyAfterCopyAndOneSuccessfulCommit() {
        val events = mutableListOf<String>()
        val result = PublicationFiles.pending(insert = { events += "insert"; "content://test/1" },
            copy = { events += "copy" }, commit = { events += "commit"; 1 }, discard = { events += "discard" })
        assertEquals("content://test/1", result)
        assertEquals(listOf("insert", "copy", "commit"), events)
    }

    @Test fun copyCommitAndCleanupFailuresNeverReturnPublishableSuccess() {
        for (step in listOf("copy", "commit")) {
            var discarded = false
            assertThrows(IllegalStateException::class.java) {
                PublicationFiles.pending<String>(insert = { "uri" }, copy = { if (step == "copy") error("copy failed") },
                    commit = { 0 }, discard = { discarded = true; error("cleanup failed") })
            }
            assertTrue(discarded)
        }
        var copied = false
        assertThrows(IllegalStateException::class.java) {
            PublicationFiles.pending<String>(insert = { null }, copy = { copied = true }, commit = { 1 }, discard = {})
        }
        assertFalse(copied)
    }

    @Test fun legacyCopyNeverOverwritesAnExistingSaveAndCopiesExactBytes() {
        val source = temp.newFile("source.mp4").apply { writeText("new media") }
        val dir = temp.newFolder()
        val existing = File(dir, "source.mp4").apply { writeText("old media") }
        val result = PublicationFiles.legacy(source, dir)
        assertNotEquals(existing.canonicalFile, result.canonicalFile)
        assertEquals("old media", existing.readText())
        assertEquals("new media", result.readText())
        assertEquals("mp4", result.extension)
        assertEquals(dir.canonicalFile, result.parentFile!!.canonicalFile)
    }

    @Test fun missingOrEmptySourceNeverCreatesASavedFile() {
        val dir = temp.newFolder()
        for (file in listOf(File(dir, "missing.mp4"), temp.newFile("empty.mp4"))) {
            assertThrows(IllegalArgumentException::class.java) { PublicationFiles.legacy(file, dir) }
        }
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
