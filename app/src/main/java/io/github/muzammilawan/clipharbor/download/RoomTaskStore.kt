package io.github.muzammilawan.clipharbor.download

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.map

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String?,
    val title: String?,
    val phase: String,
    val videoHeight: Int?,
    val audioFormatId: String?,
    val videoFormatId: String?,
    val reason: String?,
    val savedUri: String?,
    val savedName: String?,
    val savedMime: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
) {
    fun record(): TaskRecord {
        require(sourceUrl == null || TaskSources.persistable(sourceUrl) == sourceUrl)
        return TaskRecord(id, sourceUrl, title, TaskPhase.valueOf(phase), TaskSelection(videoHeight, audioFormatId, videoFormatId),
            reason, savedUri, savedName, savedMime, createdAt, updatedAt, revision)
    }
    override fun toString(): String = "TaskEntity(<private>)"

    companion object {
        fun from(row: TaskRecord): TaskEntity = TaskEntity(row.id, row.sourceUrl, row.title, row.phase.name,
            row.selection.videoHeight, row.selection.audioFormatId, row.selection.videoFormatId, row.reason,
            row.savedUri, row.savedName, row.savedMime, row.createdAt, row.updatedAt, row.revision)
    }
}

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC, id")
    abstract fun observe(): kotlinx.coroutines.flow.Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks ORDER BY createdAt, id")
    abstract suspend fun all(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun get(id: String): TaskEntity?
    @Insert
    abstract suspend fun insert(row: TaskEntity)
    @Update
    abstract suspend fun update(row: TaskEntity): Int
    @Query("DELETE FROM tasks WHERE id = :id AND revision = :revision")
    abstract suspend fun deleteRevision(id: String, revision: Long): Int

    @Transaction
    open suspend fun replace(row: TaskEntity, expectedRevision: Long): Boolean {
        if (get(row.id)?.revision != expectedRevision) return false
        return update(row) == 1
    }
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
}

class RoomTaskStore(private val dao: TaskDao) : TaskStore {
    override val rows = dao.observe().map { rows -> rows.map(TaskEntity::record) }
    override suspend fun all() = dao.all().map(TaskEntity::record)
    override suspend fun get(id: String) = dao.get(id)?.record()
    override suspend fun insert(record: TaskRecord) { dao.insert(TaskEntity.from(record)) }
    override suspend fun replace(record: TaskRecord, expectedRevision: Long) = dao.replace(TaskEntity.from(record), expectedRevision)
    override suspend fun delete(id: String, expectedRevision: Long) = dao.deleteRevision(id, expectedRevision) == 1
}
