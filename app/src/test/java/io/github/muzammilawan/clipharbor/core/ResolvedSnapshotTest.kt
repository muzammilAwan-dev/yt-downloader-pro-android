package io.github.muzammilawan.clipharbor.core

import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResolvedSnapshotTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val url = "https://example.invalid/watch?v=sample"
    private val json = """{"formats":[{"url":"https://cdn.invalid/file?sig=private"}]}"""

    private fun snapshot(clock: () -> Long = { 0L }) = ResolvedSnapshot(url, "task", json, clock)

    @Test
    fun writesExactPayloadForWorkerAndDeletesOnSuccess() {
        val directory = temporary.newFolder()
        var input: File? = null
        val result = snapshot().withFile(url, "task", directory) {
            input = it
            assertEquals(json, it.readText())
            "done"
        }
        assertEquals("done", result)
        assertFalse(input!!.exists())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun snapshotNeverPrintsSourceOrSignedPayload() {
        val input = snapshot()
        assertEquals("ResolvedSnapshot(<private>)", input.toString())
        assertFalse(input.toString().contains("private\""))
        assertFalse(input.toString().contains(url))
        input.close()
    }

    @Test
    fun deletesAfterFailureCancellationAndInterruption() {
        val directory = temporary.newFolder()
        listOf(IOException("synthetic"), CancellationException("synthetic"), InterruptedException("synthetic")).forEach { error ->
            assertThrows(error.javaClass) {
                snapshot().withFile(url, "task", directory) { throw error }
            }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun singleUseAndExplicitCloseRequireNewResolve() {
        val directory = temporary.newFolder()
        val input = snapshot()
        input.withFile(url, "task", directory) { Unit }
        assertThrows(SnapshotUnavailableException::class.java) { input.withFile(url, "task", directory) { Unit } }
        val closed = snapshot()
        closed.close()
        closed.close()
        assertThrows(SnapshotUnavailableException::class.java) { closed.withFile(url, "task", directory) { Unit } }
    }

    @Test
    fun wrongSourceOrTaskCannotReplayInput() {
        val directory = temporary.newFolder()
        assertThrows(SnapshotUnavailableException::class.java) {
            snapshot().withFile("https://other.invalid", "task", directory) { error("must not run") }
        }
        assertThrows(SnapshotUnavailableException::class.java) {
            snapshot().withFile(url, "other-task", directory) { error("must not run") }
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun staleAndNegativeAgeAreRejectedBeforeCreatingFile() {
        val directory = temporary.newFolder()
        var now = 0L
        val stale = snapshot { now }
        now = ResolvedSnapshot.MAX_AGE_MS + 1
        assertThrows(SnapshotUnavailableException::class.java) { stale.withFile(url, "task", directory) { Unit } }
        val future = snapshot { now }
        now--
        assertThrows(SnapshotUnavailableException::class.java) { future.withFile(url, "task", directory) { Unit } }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun rejectsOversizedPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedSnapshot(url, "task", "x".repeat(ResolvedSnapshot.MAX_BYTES + 1))
        }
    }

    @Test
    fun writeFailureConsumesInputWithoutLeavingFile() {
        val directory = temporary.newFile("not-a-directory")
        val input = snapshot()
        assertThrows(IllegalStateException::class.java) { input.withFile(url, "task", directory) { Unit } }
        assertThrows(SnapshotUnavailableException::class.java) { input.withFile(url, "task", directory) { Unit } }
    }

    @Test
    fun orphanCleanupOnlyRemovesOwnedRegularSnapshots() {
        val directory = temporary.newFolder()
        val orphan = File(directory, "clipharbor-resolved-123.info.json").apply { writeText(json) }
        val other = File(directory, "owner.json").apply { writeText("keep") }
        val nested = File(directory, "clipharbor-resolved-folder.info.json").apply { mkdir() }
        val child = File(nested, "keep.json").apply { writeText("keep") }
        ResolvedSnapshot.cleanOrphans(directory)
        assertFalse(orphan.exists())
        assertTrue(other.exists())
        assertTrue(child.exists())
        ResolvedSnapshot.cleanOrphans(File(directory, "absent"))
    }

    @Test
    fun replayDiscardsOldMergedSelectionAndOutputButKeepsCatalogTransport() {
        val formats = listOf(mapOf("format_id" to "18", "url" to "https://cdn.invalid/?sig=private", "http_headers" to mapOf("Referer" to url)))
        val root = mutableMapOf<String, Any>(
            "id" to "sample", "title" to "fixture", "webpage_url" to url, "formats" to formats,
            "requested_formats" to listOf("137", "140"), "requested_downloads" to listOf("137+140"),
            "format_id" to "137+140", "url" to "stale", "height" to 1080, "http_headers" to mapOf("Cookie" to "private"),
            "filepath" to "old.mp4", "_filename" to "old.mp4", "__files_to_move" to mapOf("old" to "path"),
        )
        ReplayInfo.fieldsToDiscard(root.keys, hasFormatCatalog = true).forEach(root::remove)
        assertEquals(setOf("id", "title", "webpage_url", "formats"), root.keys)
        assertTrue(root["formats"] === formats)
    }

    @Test
    fun missingFormatCatalogCannotBeReplayed() {
        assertThrows(IllegalArgumentException::class.java) {
            ReplayInfo.fieldsToDiscard(setOf("url", "format_id"), hasFormatCatalog = false)
        }
    }
}
