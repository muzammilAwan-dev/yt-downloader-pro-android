package io.github.muzammilawan.clipharbor.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.muzammilawan.clipharbor.core.TaskStage
import kotlinx.coroutines.flow.Flow

/** Suspending DAO: every call runs on Room's own executor, never the caller's thread. */
@Dao
interface TaskDao {
    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE stage IN (:stages) ORDER BY createdAt ASC")
    suspend fun byStages(stages: List<TaskStage>): List<TaskEntity>

    @Query("UPDATE tasks SET stage = :stage, updatedAt = :now WHERE id = :id")
    suspend fun updateStage(id: Long, stage: TaskStage, now: Long)

    @Query("UPDATE tasks SET progress = :percent, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, percent: Float, now: Long)

    @Query("UPDATE tasks SET stage = :stage, errorCode = :code, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: Long, stage: TaskStage, code: String?, now: Long)

    @Query("UPDATE tasks SET stage = :stage, artifactUri = :uri, updatedAt = :now WHERE id = :id")
    suspend fun markPublished(id: Long, stage: TaskStage, uri: String, now: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
