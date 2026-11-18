package io.github.muzammilawan.clipharbor.download

import org.junit.Assert.*
import org.junit.Test

class QueuePresentationTest {
    private fun row(id: String, phase: TaskPhase) = TaskRecord(id, "https://example.invalid/a", phase = phase)

    @Test fun compactViewIsBoundedKeepsActiveAndQueuedTasksFirstAndAllAccessDoesNotDropHistory() {
        val rows = (1..10).map { row("done$it", TaskPhase.DONE) } + row("queued", TaskPhase.STAGED) + row("active", TaskPhase.DOWNLOADING)
        assertEquals(5, QueuePresentation.visible(rows, false).size)
        assertEquals(listOf("active", "queued"), QueuePresentation.visible(rows, false).take(2).map { it.id })
        assertEquals(12, QueuePresentation.visible(rows, true).size)
    }

    @Test fun ActiveSavingAndCompletedTasksCannotBeReviewedAndActiveRowsCannotBeRemoved() {
        for (phase in TaskPhase.entries) {
            val row = row(phase.name, phase)
            if (phase in setOf(TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING, TaskPhase.DONE))
                assertFalse(QueuePresentation.reviewable(row))
            if (phase in TaskRepository.activePhases) assertFalse(QueuePresentation.removable(row))
        }
        assertTrue(QueuePresentation.reviewable(row("staged", TaskPhase.STAGED)))
        assertTrue(QueuePresentation.reviewable(row("paused", TaskPhase.PAUSED)))
        assertTrue(QueuePresentation.removable(row("done", TaskPhase.DONE)))
    }

    @Test fun batchCountsCoverOnlyWaitingLinksAndFinishedRecords() {
        val rows = listOf(row("queued1", TaskPhase.STAGED), row("queued2", TaskPhase.STAGED),
            row("active", TaskPhase.DOWNLOADING), row("paused", TaskPhase.PAUSED), row("chooser", TaskPhase.CHOOSING),
            row("done", TaskPhase.DONE), row("failed", TaskPhase.FAILED), row("canceled", TaskPhase.CANCELED))
        assertEquals(2, QueuePresentation.queuedCount(rows))
        assertEquals(3, QueuePresentation.finishedCount(rows))
        assertEquals(0, QueuePresentation.queuedCount(emptyList()))
        assertEquals(0, QueuePresentation.finishedCount(emptyList()))
    }
}
