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
        assertEquals(4, store.rows.value.count { it.phase == TaskPhase.PAUSED && it.reason == "PROCESS_INTERRUPTED" })
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
