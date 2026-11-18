package io.github.muzammilawan.clipharbor.download

import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ErrorRecovery

object QueuePresentation {
    fun visible(rows: List<TaskRecord>, all: Boolean): List<TaskRecord> {
        val ordered = rows.sortedBy { when (it.phase) {
            TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING, TaskPhase.CHOOSING -> 0
            TaskPhase.STAGED, TaskPhase.PAUSED, TaskPhase.FAILED -> 1
            else -> 2
        } }
        return if (all) ordered else ordered.take(5)
    }
    fun reviewable(row: TaskRecord): Boolean = if (row.phase == TaskPhase.FAILED)
        runCatching { ErrorRecovery.forCode(ErrorCode.valueOf(row.reason.orEmpty())).freshResolve }.getOrDefault(false)
        else row.phase in setOf(TaskPhase.STAGED, TaskPhase.PAUSED, TaskPhase.CANCELED, TaskPhase.CHOOSING)
    fun removable(row: TaskRecord): Boolean = row.phase !in TaskRepository.activePhases
    /** Links waiting to start; batch cancel applies to these only. */
    fun queuedCount(rows: List<TaskRecord>): Int = rows.count { it.phase == TaskPhase.STAGED }
    /** Records a batch "clear finished" may forget; saved files stay on disk. */
    fun finishedCount(rows: List<TaskRecord>): Int = rows.count { it.phase in TaskRepository.finishedPhases }
}
