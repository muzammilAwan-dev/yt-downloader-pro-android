package io.github.muzammilawan.clipharbor.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Exercises production mapper/CAS DAO bodies with a fake table; Android SQLite execution remains device-deferred. */
class RoomTaskStoreTest {
    private val dao = object : TaskDao() {
        val table = MutableStateFlow<List<TaskEntity>>(emptyList())
        override fun observe() = table
        override suspend fun all() = table.value
        override suspend fun get(id: String) = table.value.singleOrNull { it.id == id }
        override suspend fun insert(row: TaskEntity) { check(get(row.id) == null); table.value += row }
        override suspend fun update(row: TaskEntity): Int {
            if (get(row.id) == null) return 0
            table.value = table.value.map { if (it.id == row.id) row else it }; return 1
        }
        override suspend fun deleteRevision(id: String, revision: Long): Int {
            if (get(id)?.revision != revision) return 0
            table.value = table.value.filter { it.id != id }; return 1
        }
    }
    private val store = RoomTaskStore(dao)

    @Test fun allRecordFieldsRoundTripWithoutSensitivePayloadColumns() = runTest {
        val row = TaskRecord("id", "https://example.invalid/watch?v=x", "title", TaskPhase.DONE,
            TaskSelection(audioFormatId = "251-drc"), null, "content://test/1", "audio.webm", "audio/webm", 10, 20, 2)
        store.insert(row)
        assertEquals(row, store.get("id"))
        assertEquals(listOf(row), store.all())
        assertFalse(TaskEntity::class.java.declaredFields.any { it.name in setOf("cookies", "headers", "formats", "snapshot", "stderr") })
    }

    @Test fun productionDaoCasBodiesRejectStaleReplaceAndDelete() = runTest {
        val row = TaskRecord("id", "https://example.invalid/a")
        store.insert(row)
        assertFalse(store.replace(row.copy(revision = 2), 9))
        assertTrue(store.replace(row.copy(revision = 1), 0))
        assertFalse(store.delete("id", 0))
        assertTrue(store.delete("id", 1))
        assertNull(store.get("id"))
    }

    @Test fun corruptRowsAndUnvalidatedSensitiveSourcesFailClosed() {
        val row = TaskEntity.from(TaskRecord("id", "https://example.invalid/a"))
        assertThrows(IllegalArgumentException::class.java) { row.copy(phase = "NOT_A_PHASE").record() }
        assertThrows(IllegalArgumentException::class.java) { row.copy(sourceUrl = "https://example.invalid/?sig=secret").record() }
        assertThrows(IllegalArgumentException::class.java) { row.copy(audioFormatId = "bad']").record() }
        assertFalse(row.toString().contains("example"))
    }
}
