package io.github.muzammilawan.clipharbor.core

/**
 * Durable task stages for the planned Room queue (docs/02-ARCHITECTURE.md §2).
 *
 * Deliberately separate from [Stage]: [Stage] classifies one yt-dlp invocation for error mapping, while this enum
 * describes a whole task that can be queued, paused and retried across process deaths. Reusing [Stage] would force
 * the engine taxonomy to carry queue states it never emits.
 */
enum class TaskStage {
    QUEUED, RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING, DONE, PAUSED, FAILED, CANCELED;

    /** Work is still intended to progress; used for busy UI and notification refresh. */
    val isActive: Boolean
        get() = this in setOf(QUEUED, RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING)

    /** No further transition is possible without creating a new task. */
    val isTerminal: Boolean
        get() = this == DONE || this == CANCELED
}

/**
 * Pure transition rules. Persistence, retries and side effects belong to the repository and worker; this object only
 * decides whether a proposed next stage is legal, so the rule is unit-tested without Android or Room.
 */
object TaskTransitions {
    /** Forward edges only. Audio-only tasks legally skip [TaskStage.MERGING]; a video task may skip it when none ran. */
    private val allowed: Map<TaskStage, Set<TaskStage>> = mapOf(
        TaskStage.QUEUED to setOf(TaskStage.RESOLVING),
        TaskStage.RESOLVING to setOf(TaskStage.DOWNLOADING),
        TaskStage.DOWNLOADING to setOf(TaskStage.MERGING, TaskStage.POST_PROCESSING, TaskStage.PUBLISHING),
        TaskStage.MERGING to setOf(TaskStage.POST_PROCESSING, TaskStage.PUBLISHING),
        TaskStage.POST_PROCESSING to setOf(TaskStage.PUBLISHING),
        TaskStage.PUBLISHING to setOf(TaskStage.DONE),
        TaskStage.PAUSED to setOf(TaskStage.QUEUED),
        // Retry is gated separately by the retryable argument below.
        TaskStage.FAILED to setOf(TaskStage.QUEUED),
        TaskStage.DONE to emptySet(),
        TaskStage.CANCELED to emptySet(),
    )

    /** Any non-terminal stage may pause, fail or be canceled (docs/02 §2). */
    private val interruptionTargets = setOf(TaskStage.PAUSED, TaskStage.FAILED, TaskStage.CANCELED)

    /**
     * True when [to] is a legal next stage from [from]. Re-persisting the same stage is allowed so an idempotent
     * re-run of a stage does not have to special-case the writer.
     *
     * [retryable] only affects FAILED -> QUEUED; every other transition ignores it.
     */
    fun canTransition(from: TaskStage, to: TaskStage, retryable: Boolean = false): Boolean {
        if (from == to) return true
        if (from.isTerminal) return false
        if (to in interruptionTargets) return true
        if (from == TaskStage.FAILED && to == TaskStage.QUEUED) return retryable
        return to in allowed.getValue(from)
    }

    /** Throwing form for call sites that must not silently drop an illegal transition. */
    fun requireTransition(from: TaskStage, to: TaskStage, retryable: Boolean = false) {
        require(canTransition(from, to, retryable)) {
            "Illegal task transition $from -> $to (retryable=$retryable)"
        }
    }
}
