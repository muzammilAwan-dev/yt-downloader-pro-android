package io.github.muzammilawan.clipharbor.download

import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.log.LogRedactor
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TaskPhase { STAGED, RESOLVING, CHOOSING, DOWNLOADING, PUBLISHING, PAUSED, DONE, FAILED, CANCELED }

data class TaskSelection(val videoHeight: Int? = null, val audioFormatId: String? = null, val videoFormatId: String? = null) {
    init {
        require(videoHeight == null || videoHeight in setOf(144, 240, 360, 480, 720, 1080, 1440, 2160))
        require(listOf(videoHeight, audioFormatId, videoFormatId).count { it != null } <= 1)
        require(listOfNotNull(audioFormatId, videoFormatId).all { Regex("[A-Za-z0-9_.-]{1,128}").matches(it) })
    }
    override fun toString(): String = "TaskSelection(<private>)"
}

/** No formats, direct media URLs, replay, options, cookies, headers or stderr belong in this record. */
data class TaskRecord(
    val id: String,
    val sourceUrl: String?,
    val title: String? = null,
    val phase: TaskPhase = TaskPhase.STAGED,
    val selection: TaskSelection = TaskSelection(),
    val reason: String? = null,
    val savedUri: String? = null,
    val savedName: String? = null,
    val savedMime: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val revision: Long = 0,
) {
    init {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(id))
        require(sourceUrl == null || TaskSources.persistable(sourceUrl) == sourceUrl)
    }
    override fun toString(): String = "TaskRecord(phase=$phase)"
}

object TaskSources {
    /** Default deny query persistence; unsafe source links stay process-private and require re-entry after restart. */
    fun persistable(url: String): String? {
        require(MediaEntry.pasted(url) is MediaEntry.Input.Link)
        val uri = URI(url)
        if (uri.rawFragment != null) return null
        val query = uri.rawQuery ?: return url
        if (query.isEmpty()) return null
        return url.takeIf { query.split('&').all { part ->
            val pair = part.split('=', limit = 2)
            pair.size == 2 && pair[0] in setOf("v", "id") && Regex("[A-Za-z0-9_-]{1,128}").matches(pair[1])
        } }
    }
}

interface TaskStore {
    val rows: Flow<List<TaskRecord>>
    suspend fun all(): List<TaskRecord>
    suspend fun get(id: String): TaskRecord?
    suspend fun insert(record: TaskRecord)
    suspend fun replace(record: TaskRecord, expectedRevision: Long): Boolean
    suspend fun delete(id: String, expectedRevision: Long): Boolean
}

/** Serialized state boundary. Room also checks revisions atomically, so stale owners cannot overwrite newer work. */
class TaskRepository(private val store: TaskStore, private val clockMs: () -> Long = System::currentTimeMillis) {
    val rows = store.rows
    private val lock = Mutex()
    private var recovered = false

    suspend fun stage(id: String, url: String): TaskRecord = lock.withLock {
        check(store.all().size < MAX_RECORDS) { "Queue is full; remove old records before adding another link" }
        val now = clockMs()
        TaskRecord(id, TaskSources.persistable(url), createdAt = now, updatedAt = now).also { store.insert(it) }
    }

    suspend fun get(id: String): TaskRecord? = store.get(id)

    /** Late Activity/session cleanup may dismiss only the exact chooser revision it owned. */
    suspend fun dismissChooser(id: String, expectedRevision: Long): Boolean = lock.withLock {
        val previous = store.get(id) ?: return@withLock false
        if (previous.phase != TaskPhase.CHOOSING || previous.revision != expectedRevision) return@withLock false
        store.replace(previous.copy(phase = TaskPhase.CANCELED, reason = "CHOOSER_DISMISSED",
            revision = previous.revision + 1, updatedAt = clockMs()), previous.revision)
    }

    suspend fun transition(id: String, next: TaskPhase, selection: TaskSelection? = null, title: String? = null,
        reason: String? = null, savedUri: String? = null, savedName: String? = null, savedMime: String? = null): TaskRecord = lock.withLock {
        val previous = checkNotNull(store.get(id)) { "Task record unavailable" }
        check(allowed(previous.phase, next)) { "Task transition not allowed" }
        require(reason == null || Regex("[A-Z0-9_]{1,64}").matches(reason))
        require(savedUri == null || savedUri.startsWith("content://"))
        val changed = previous.copy(phase = next, selection = selection ?: previous.selection,
            title = title?.let(LogRedactor::redactLine)?.take(300) ?: previous.title, reason = reason, savedUri = savedUri,
            savedName = savedName?.let(LogRedactor::redactLine)?.take(300), savedMime = savedMime?.take(100), updatedAt = clockMs(), revision = previous.revision + 1)
        check(store.replace(changed, previous.revision)) { "Task changed by another owner" }
        changed
    }

    /** Run once per process before admission, never on Activity/session recreation. No automatic rescheduling. */
    suspend fun recoverInterrupted() = lock.withLock {
        if (recovered) return@withLock
        store.all().filter { it.phase in activePhases }.forEach { previous ->
            check(store.replace(previous.copy(phase = TaskPhase.PAUSED,
                reason = if (previous.phase == TaskPhase.PUBLISHING) "PUBLICATION_INTERRUPTED" else "PROCESS_INTERRUPTED", savedUri = null,
                updatedAt = clockMs(), revision = previous.revision + 1), previous.revision))
        }
        recovered = true
    }

    suspend fun remove(id: String): Boolean = lock.withLock {
        val previous = store.get(id) ?: return@withLock false
        if (previous.phase in activePhases) return@withLock false
        store.delete(id, previous.revision)
    }

    private fun allowed(from: TaskPhase, to: TaskPhase): Boolean = when (from) {
        TaskPhase.STAGED, TaskPhase.PAUSED, TaskPhase.FAILED, TaskPhase.CANCELED -> to in setOf(TaskPhase.RESOLVING, TaskPhase.CANCELED)
        TaskPhase.RESOLVING -> to in setOf(TaskPhase.CHOOSING, TaskPhase.PAUSED, TaskPhase.FAILED, TaskPhase.CANCELED)
        TaskPhase.CHOOSING -> to in setOf(TaskPhase.CHOOSING, TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PAUSED, TaskPhase.FAILED, TaskPhase.CANCELED)
        TaskPhase.DOWNLOADING -> to in setOf(TaskPhase.PUBLISHING, TaskPhase.PAUSED, TaskPhase.FAILED, TaskPhase.CANCELED)
        TaskPhase.PUBLISHING -> to in setOf(TaskPhase.DONE, TaskPhase.PAUSED, TaskPhase.FAILED)
        TaskPhase.DONE -> false
    }

    companion object {
        const val MAX_RECORDS = 200
        val activePhases = setOf(TaskPhase.RESOLVING, TaskPhase.CHOOSING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING)
    }
}
