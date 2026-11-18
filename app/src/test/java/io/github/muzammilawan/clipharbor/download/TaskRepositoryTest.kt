package io.github.muzammilawan.clipharbor.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TaskRepositoryTest {
    private val store = MemoryTaskStore()
    private val repository = TaskRepository(store, clockMs = { 123L })

    @Test fun stagedTasksAreDurableInputsOnlyAndSourcesNeverContainSignedOrSecretQueries() = runTest {
        val safe = repository.stage("one", "https://example.invalid/watch?v=test")
        assertEquals(TaskPhase.STAGED, safe.phase)
        assertEquals("https://example.invalid/watch?v=test", safe.sourceUrl)
        listOf("token=secret", "sig=secret", "Cookie=secret", "unknown=secret", "v=id&po_token=secret", "v=id#secret").forEachIndexed { i, q ->
            val row = repository.stage("unsafe$i", "https://example.invalid/watch?$q")
            assertNull(row.sourceUrl)
            assertFalse(row.toString().contains("secret"))
        }
        assertThrows(IllegalArgumentException::class.java) { TaskSources.persistable("http://example.invalid") }
    }

    @Test fun transitionsFenceStaleWritersAndTerminalRecords() = runTest {
        repository.stage("one", "https://example.invalid/a")
        val resolving = repository.transition("one", TaskPhase.RESOLVING)
        assertEquals(1L, resolving.revision)
        assertFalse(store.replace(resolving.copy(phase = TaskPhase.DONE), 0L))
        repository.transition("one", TaskPhase.CHOOSING)
        repository.transition("one", TaskPhase.DOWNLOADING)
        repository.transition("one", TaskPhase.PUBLISHING)
        repository.transition("one", TaskPhase.DONE, savedUri = "content://test/1")
        assertEquals(TaskPhase.DONE, store.rows.value.single().phase)
        try { repository.transition("one", TaskPhase.DOWNLOADING); fail("terminal restarted") } catch (_: IllegalStateException) {}
    }

    @Test fun newProcessRecoveryPausesInterruptedTasksNeverDownloadsOrMarksPublicationDone() = runTest {
        for (phase in listOf(TaskPhase.RESOLVING, TaskPhase.CHOOSING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING)) {
            store.insert(TaskRecord(phase.name, "https://example.invalid/a", phase = phase))
        }
        store.insert(TaskRecord("queued", "https://example.invalid/a"))
        store.insert(TaskRecord("done", "https://example.invalid/a", phase = TaskPhase.DONE, savedUri = "content://test/1"))
        repository.recoverInterrupted()
        assertEquals(3, store.rows.value.count { it.phase == TaskPhase.PAUSED && it.reason == "PROCESS_INTERRUPTED" })
        assertEquals("PUBLICATION_INTERRUPTED", store.rows.value.first { it.id == "PUBLISHING" }.reason)
        assertEquals(TaskPhase.STAGED, store.rows.value.first { it.id == "queued" }.phase)
        assertEquals(TaskPhase.DONE, store.rows.value.first { it.id == "done" }.phase)
        repository.recoverInterrupted()
        assertTrue(store.rows.value.filter { it.phase == TaskPhase.PAUSED }.all { it.revision == 1L })
    }

    @Test fun retryAlwaysReturnsToResolveAndSavedResultsCannotBeAutomaticallyRepeated() = runTest {
        repository.stage("one", "https://example.invalid/a")
        repository.transition("one", TaskPhase.RESOLVING)
        repository.transition("one", TaskPhase.FAILED, reason = "HTTP_403_ON_MEDIA")
        assertEquals(TaskPhase.RESOLVING, repository.transition("one", TaskPhase.RESOLVING).phase)
        assertNull(store.rows.value.single().reason)
        assertNull(store.rows.value.single().savedUri)
    }

    @Test fun queueIsBoundedAndTitlesDoNotRetainSecretHeaders() = runTest {
        repeat(TaskRepository.MAX_RECORDS) { store.insert(TaskRecord("row$it", "https://example.invalid/a")) }
        try { repository.stage("overflow", "https://example.invalid/a"); fail("unbounded") } catch (_: IllegalStateException) {}
        repository.transition("row0", TaskPhase.RESOLVING)
        repository.transition("row0", TaskPhase.CHOOSING, title = "Cookie: sensitive=value")
        assertFalse(store.rows.value.first().title!!.contains("sensitive=value"))
        assertThrows(IllegalArgumentException::class.java) { TaskRecord("invalid", "https://example.invalid/a?token=secret") }
    }

    @Test fun lateChooserDismissalCannotCancelANewerResolveOrDownloadRevision() = runTest {
        repository.stage("one", "https://example.invalid/a")
        repository.transition("one", TaskPhase.RESOLVING)
        val chooser = repository.transition("one", TaskPhase.CHOOSING)
        repository.transition("one", TaskPhase.RESOLVING)
        assertFalse(repository.dismissChooser("one", chooser.revision))
        val newer = repository.transition("one", TaskPhase.CHOOSING)
        assertFalse(repository.dismissChooser("one", chooser.revision))
        assertTrue(repository.dismissChooser("one", newer.revision))
        assertEquals(TaskPhase.CANCELED, store.rows.value.single().phase)
    }

    @Test fun removalCannotDeleteActiveOrSavingTasksAndOnlySafeSelectionDataIsStored() = runTest {
        repository.stage("one", "https://example.invalid/a")
        repository.transition("one", TaskPhase.RESOLVING)
        assertFalse(repository.remove("one"))
        repository.transition("one", TaskPhase.CHOOSING, selection = TaskSelection(videoHeight = 720))
        assertEquals(720, store.rows.value.single().selection.videoHeight)
        repository.transition("one", TaskPhase.CANCELED)
        assertTrue(repository.remove("one"))
        assertTrue(store.rows.value.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { TaskSelection(audioFormatId = "bad']+b") }
        assertThrows(IllegalArgumentException::class.java) { TaskSelection(videoHeight = 720, audioFormatId = "140") }
    }

    @Test fun cancellingTheQueueKeepsTheRunningAttemptAndFinishedHistory() = runTest {
        repository.stage("waiting1", "https://example.invalid/a")
        repository.stage("waiting2", "https://example.invalid/a")
        repository.stage("running", "https://example.invalid/a")
        repository.transition("running", TaskPhase.RESOLVING)
        repository.transition("running", TaskPhase.CHOOSING)
        repository.transition("running", TaskPhase.DOWNLOADING)
        repository.stage("saved", "https://example.invalid/a")
        repository.transition("saved", TaskPhase.RESOLVING)
        repository.transition("saved", TaskPhase.FAILED, reason = "HTTP_403_ON_MEDIA")

        assertEquals(2, repository.cancelQueued())
        val byId = store.rows.value.associateBy { it.id }
        assertEquals(TaskPhase.CANCELED, byId.getValue("waiting1").phase)
        assertEquals("QUEUE_CANCELED", byId.getValue("waiting2").reason)
        assertEquals(TaskPhase.DOWNLOADING, byId.getValue("running").phase)
        assertEquals(TaskPhase.FAILED, byId.getValue("saved").phase)
        assertEquals(0, repository.cancelQueued())
    }

    @Test fun clearingFinishedRecordsKeepsQueuedPausedAndActiveWork() = runTest {
        repository.stage("waiting", "https://example.invalid/a")
        repository.stage("paused", "https://example.invalid/a")
        repository.transition("paused", TaskPhase.RESOLVING)
        repository.transition("paused", TaskPhase.PAUSED, reason = "USER_PAUSED")
        repository.stage("running", "https://example.invalid/a")
        repository.transition("running", TaskPhase.RESOLVING)
        repository.transition("running", TaskPhase.CHOOSING)
        repository.stage("failed", "https://example.invalid/a")
        repository.transition("failed", TaskPhase.RESOLVING)
        repository.transition("failed", TaskPhase.FAILED, reason = "UNAVAILABLE")
        repository.stage("done", "https://example.invalid/a")
        repository.transition("done", TaskPhase.RESOLVING)
        repository.transition("done", TaskPhase.CHOOSING)
        repository.transition("done", TaskPhase.DOWNLOADING)
        repository.transition("done", TaskPhase.PUBLISHING)
        repository.transition("done", TaskPhase.DONE, savedUri = "content://test/1")

        assertEquals(2, repository.clearFinished())
        assertEquals(setOf("waiting", "paused", "running"), store.rows.value.map { it.id }.toSet())
        assertEquals(0, repository.clearFinished())
    }

    @Test fun batchActionsReReadEachRowSoAChangedRevisionIsSkippedNotOverwritten() = runTest {
        repository.stage("one", "https://example.invalid/a")
        repository.stage("two", "https://example.invalid/a")
        val stale = MemoryTaskStore().apply {
            rows.value = store.rows.value
        }
        // A listing that claims both links are still waiting while the real store moved one on.
        val staler = object : TaskStore {
            override val rows = store.rows
            override suspend fun all() = stale.rows.value
            override suspend fun get(id: String) = store.get(id)
            override suspend fun insert(record: TaskRecord) = store.insert(record)
            override suspend fun replace(record: TaskRecord, expectedRevision: Long) = store.replace(record, expectedRevision)
            override suspend fun delete(id: String, expectedRevision: Long) = store.delete(id, expectedRevision)
        }
        repository.transition("two", TaskPhase.RESOLVING)
        val fenced = TaskRepository(staler)
        assertEquals(1, fenced.cancelQueued())
        assertEquals(TaskPhase.CANCELED, store.rows.value.first { it.id == "one" }.phase)
        assertEquals(TaskPhase.RESOLVING, store.rows.value.first { it.id == "two" }.phase)
    }
}

class MemoryTaskStore : TaskStore {
    override val rows = MutableStateFlow<List<TaskRecord>>(emptyList())
    override suspend fun insert(record: TaskRecord) {
        check(rows.value.none { it.id == record.id })
        rows.value = rows.value + record
    }
    override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun all() = rows.value
    override suspend fun replace(record: TaskRecord, expectedRevision: Long): Boolean {
        if (get(record.id)?.revision != expectedRevision) return false
        rows.value = rows.value.map { if (it.id == record.id) record else it }
        return true
    }
    override suspend fun delete(id: String, expectedRevision: Long): Boolean {
        if (get(id)?.revision != expectedRevision) return false
        rows.value = rows.value.filter { it.id != id }
        return true
    }
}
