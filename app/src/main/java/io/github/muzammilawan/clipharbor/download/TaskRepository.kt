package io.github.muzammilawan.clipharbor.download

import io.github.muzammilawan.clipharbor.core.TaskStage
import io.github.muzammilawan.clipharbor.core.TaskTransitions
import kotlinx.coroutines.flow.Flow

/**
 * Every write goes through [TaskTransitions] first, so the persisted stage can never be illegal. Side effects
 * (engine call, notification, publish) live in the worker, not here: this class only persists and validates.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val safeFormatId = Regex("[A-Za-z0-9_.-]{1,128}")
    private val allowedContainers = setOf("mp4", "mkv", "mp4/mkv")

    /**
     * Creation is the only path that bypasses [TaskTransitions]; every later change is checked.
     * Validation mirrors [io.github.muzammilawan.clipharbor.core.MediaEntry.pasted] and
     * [io.github.muzammilawan.clipharbor.engine.DownloadExecutor] so a caller cannot persist an unusable row.
     */
    suspend fun create(
        url: String,
        mediaMode: TaskMediaMode,
        requestedResolution: Int,
        audioFormatId: String?,
        mergeContainer: String,
    ): Long {
        require(url.startsWith("https://") && url.none { it.isISOControl() }) { "A full HTTPS link is required" }
        require(mergeContainer in allowedContainers) { "Unsupported merge container" }
        when (mediaMode) {
            TaskMediaMode.VIDEO -> {
                require(requestedResolution > 0) { "Video needs a positive short-edge resolution" }
                require(audioFormatId == null) { "Video must not carry an audio-only format id" }
            }
            TaskMediaMode.AUDIO -> {
                require(requestedResolution == 0) { "Audio-only has no resolution" }
                require(audioFormatId != null && safeFormatId.matches(audioFormatId)) {
                    "Audio-only needs a safe audio format id"
                }
            }
        }
        val now = clockMs()
        return dao.insert(
            TaskEntity(
                url = url,
                mediaMode = mediaMode,
                requestedResolution = requestedResolution,
                audioFormatId = audioFormatId,
                mergeContainer = mergeContainer,
                stage = TaskStage.QUEUED,
                progress = 0f,
                errorCode = null,
                artifactUri = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun advance(id: Long, to: TaskStage) {
        val task = dao.byId(id) ?: error("No task $id")
        TaskTransitions.requireTransition(task.stage, to)
        dao.updateStage(id, to, clockMs())
    }

    suspend fun progress(id: Long, percent: Float) {
        require(percent.isFinite() && percent in 0f..100f) { "Progress must be within 0..100" }
        val task = dao.byId(id) ?: return
        if (task.stage != TaskStage.DOWNLOADING) return
        dao.updateProgress(id, percent, clockMs())
    }

    suspend fun pause(id: Long) {
        val task = dao.byId(id) ?: return
        TaskTransitions.requireTransition(task.stage, TaskStage.PAUSED)
        dao.updateStage(id, TaskStage.PAUSED, clockMs())
    }

    suspend fun fail(id: Long, errorCode: String) {
        require(errorCode.isNotBlank()) { "Error code must not be blank" }
        val task = dao.byId(id) ?: return
        TaskTransitions.requireTransition(task.stage, TaskStage.FAILED)
        dao.markFailed(id, TaskStage.FAILED, errorCode, clockMs())
    }

    /** Retry only proceeds when the [io.github.muzammilawan.clipharbor.core.ErrorCode] was marked retryable. */
    suspend fun retry(id: Long, retryable: Boolean) {
        val task = dao.byId(id) ?: return
        TaskTransitions.requireTransition(task.stage, TaskStage.QUEUED, retryable)
        dao.updateStage(id, TaskStage.QUEUED, clockMs())
    }

    suspend fun cancel(id: Long) {
        val task = dao.byId(id) ?: return
        if (task.stage.isTerminal) return
        TaskTransitions.requireTransition(task.stage, TaskStage.CANCELED)
        dao.updateStage(id, TaskStage.CANCELED, clockMs())
    }

    suspend fun publish(id: Long, artifactUri: String) {
        require(artifactUri.startsWith("content://") || artifactUri.startsWith("file://")) {
            "Artifact URI must be content:// or file://"
        }
        val task = dao.byId(id) ?: error("No task $id")
        TaskTransitions.requireTransition(task.stage, TaskStage.DONE)
        dao.markPublished(id, TaskStage.DONE, artifactUri, clockMs())
    }

    suspend fun byId(id: Long): TaskEntity? = dao.byId(id)

    suspend fun active(): List<TaskEntity> = dao.byStages(
        listOf(TaskStage.QUEUED, TaskStage.RESOLVING, TaskStage.DOWNLOADING,
            TaskStage.MERGING, TaskStage.POST_PROCESSING, TaskStage.PUBLISHING),
    )

    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
}
