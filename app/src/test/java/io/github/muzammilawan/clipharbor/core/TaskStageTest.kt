package io.github.muzammilawan.clipharbor.core

import io.github.muzammilawan.clipharbor.core.TaskStage.CANCELED
import io.github.muzammilawan.clipharbor.core.TaskStage.DONE
import io.github.muzammilawan.clipharbor.core.TaskStage.DOWNLOADING
import io.github.muzammilawan.clipharbor.core.TaskStage.FAILED
import io.github.muzammilawan.clipharbor.core.TaskStage.MERGING
import io.github.muzammilawan.clipharbor.core.TaskStage.PAUSED
import io.github.muzammilawan.clipharbor.core.TaskStage.POST_PROCESSING
import io.github.muzammilawan.clipharbor.core.TaskStage.PUBLISHING
import io.github.muzammilawan.clipharbor.core.TaskStage.QUEUED
import io.github.muzammilawan.clipharbor.core.TaskStage.RESOLVING
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskStageTest {

    @Test
    fun `video happy path is the documented forward chain`() {
        val path = listOf(QUEUED, RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING, DONE)
        path.zipWithNext().forEach { (from, to) ->
            assertTrue("$from -> $to must be legal", TaskTransitions.canTransition(from, to))
        }
    }

    @Test
    fun `audio only may skip merging`() {
        assertTrue(TaskTransitions.canTransition(DOWNLOADING, PUBLISHING))
        assertTrue(TaskTransitions.canTransition(DOWNLOADING, POST_PROCESSING))
        assertTrue(TaskTransitions.canTransition(MERGING, PUBLISHING))
        assertTrue(TaskTransitions.canTransition(POST_PROCESSING, PUBLISHING))
    }

    @Test
    fun `backwards and skipped forward edges are rejected`() {
        assertFalse(TaskTransitions.canTransition(QUEUED, DOWNLOADING))
        assertFalse(TaskTransitions.canTransition(RESOLVING, MERGING))
        assertFalse(TaskTransitions.canTransition(DOWNLOADING, RESOLVING))
        assertFalse(TaskTransitions.canTransition(POST_PROCESSING, DOWNLOADING))
        assertFalse(TaskTransitions.canTransition(PUBLISHING, MERGING))
        assertFalse(TaskTransitions.canTransition(DONE, PUBLISHING))
    }

    @Test
    fun `every non terminal stage may pause fail or cancel`() {
        TaskStage.entries.filterNot { it.isTerminal }.forEach { from ->
            listOf(PAUSED, FAILED, CANCELED).forEach { to ->
                assertTrue("$from -> $to must be legal", TaskTransitions.canTransition(from, to))
            }
        }
    }

    @Test
    fun `paused resumes only through the queue`() {
        assertTrue(TaskTransitions.canTransition(PAUSED, QUEUED))
        assertFalse(TaskTransitions.canTransition(PAUSED, RESOLVING))
        assertFalse(TaskTransitions.canTransition(PAUSED, DOWNLOADING))
    }

    @Test
    fun `retry from failed is gated by the retryable flag`() {
        assertFalse(TaskTransitions.canTransition(FAILED, QUEUED))
        assertTrue(TaskTransitions.canTransition(FAILED, QUEUED, retryable = true))
        // Retryable does not authorize skipping the queue or any other edge.
        assertFalse(TaskTransitions.canTransition(FAILED, RESOLVING, retryable = true))
        assertFalse(TaskTransitions.canTransition(FAILED, DOWNLOADING, retryable = true))
        assertFalse(TaskTransitions.canTransition(FAILED, DONE, retryable = true))
    }

    @Test
    fun `done and canceled are immutable`() {
        listOf(DONE, CANCELED).forEach { terminal ->
            TaskStage.entries.filter { it != terminal }.forEach { other ->
                assertFalse("$terminal -> $other must be rejected", TaskTransitions.canTransition(terminal, other))
            }
        }
    }

    @Test
    fun `re persisting the same stage is idempotent`() {
        TaskStage.entries.forEach { stage ->
            assertTrue("$stage -> $stage must be accepted", TaskTransitions.canTransition(stage, stage))
        }
    }

    @Test
    fun `active and terminal classification matches the documented model`() {
        val active = setOf(QUEUED, RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING)
        TaskStage.entries.forEach { stage ->
            assertTrue("$stage active mismatch", stage.isActive == (stage in active))
            assertTrue("$stage terminal mismatch", stage.isTerminal == (stage == DONE || stage == CANCELED))
        }
    }

    @Test
    fun `requireTransition accepts legal and rejects illegal`() {
        TaskTransitions.requireTransition(QUEUED, RESOLVING)
        TaskTransitions.requireTransition(FAILED, QUEUED, retryable = true)
        try {
            TaskTransitions.requireTransition(DONE, QUEUED)
            fail("DONE -> QUEUED must throw")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
