package io.github.muzammilawan.clipharbor.download

object QueuePresentation {
    fun visible(rows: List<TaskRecord>, all: Boolean): List<TaskRecord> {
        val ordered = rows.sortedBy { when (it.phase) {
            TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING, TaskPhase.CHOOSING -> 0
            TaskPhase.STAGED, TaskPhase.PAUSED, TaskPhase.FAILED -> 1
            else -> 2
        } }
        return if (all) ordered else ordered.take(5)
    }
    fun reviewable(row: TaskRecord): Boolean = row.phase in setOf(TaskPhase.STAGED, TaskPhase.PAUSED, TaskPhase.FAILED,
        TaskPhase.CANCELED, TaskPhase.CHOOSING)
    fun removable(row: TaskRecord): Boolean = row.phase !in TaskRepository.activePhases
}
