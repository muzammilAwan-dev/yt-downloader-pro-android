package io.github.muzammilawan.clipharbor.core

import io.github.muzammilawan.clipharbor.feature.home.HomeController
import io.github.muzammilawan.clipharbor.feature.home.HomePhase
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedSessionOwnerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=session"
    private val fake = FakeEngine()
    private var serial = 0
    private var creations = 0
    private var stops = 0
    private var publishes = 0
    private var releases = 0
    private val finishDownload = CompletableDeferred<Unit>()
    private var admission: CompletableDeferred<Unit>? = null
    private val snapshots = mutableListOf<ResolvedSnapshot>()
    private val requests = mutableListOf<DownloadRequest>()
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            val snapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}")
            snapshots += snapshot
            it.copy(resolvedSnapshot = snapshot)
        }

        override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit): Result<DownloadOutcome> {
            requests += request
            finishDownload.await()
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "session.mp4").apply { writeText("synthetic media") }
            return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList()))
        }
    }

    private fun sessions(scope: CoroutineScope) = SharedSessionOwner(scope.coroutineContext, create = { owned ->
        creations++
        HomeController(engine, owned, temporary.newFolder(), publish = { file ->
            publishes++
            PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4")
        }, newTaskId = { "task-${++serial}" }, foreground = object : DownloadForeground {
            override suspend fun acquire(taskId: String): DownloadForeground.Lease {
                admission?.await()
                return object : DownloadForeground.Lease {
                    override fun progress(percent: Int) {}
                    override fun publishing() {}
                    override fun close() { releases++ }
                }
            }
        })
    }, stop = { home -> stops++; home.close() })

    private fun choose(home: HomeController) {
        home.editUrl(url)
        home.resolve()
    }

    private fun assertSnapshotClosed(index: Int = 0) {
        assertThrows(IllegalStateException::class.java) {
            snapshots[index].withFile(url, "task-${index + 1}", temporary.newFolder()) { error("must not run") }
        }
    }

    @Test fun attachmentsShareControllerScopeAndInitialization() = runTest {
        val sessions = sessions(this)
        val first = sessions.attach()
        val second = sessions.attach()
        assertSame(first.value, second.value)
        assertSame(first.scope, second.scope)
        first.value.initialize(); advanceUntilIdle()
        second.value.initialize(); advanceUntilIdle()
        assertEquals(1, creations)
        assertEquals(1, fake.initCalls)
        first.close()
        assertEquals(0, stops)
        assertTrue(second.scope.coroutineContext[Job]!!.isActive)
        second.close(); advanceUntilIdle()
        assertEquals(1, stops)
    }

    @Test fun uiReconnectionKeepsDownloadWhileAnotherOwnerIsAttached() = runTest {
        val sessions = sessions(this)
        val ui = sessions.attach()
        val otherOwner = sessions.attach() // Future service lease; not an Android service test.
        ui.value.initialize(); advanceUntilIdle()
        choose(ui.value); advanceUntilIdle()
        ui.value.download(); runCurrent()
        ui.close()
        val reconnected = sessions.attach()
        assertSame(otherOwner.value, reconnected.value)
        assertEquals(HomePhase.DOWNLOADING, reconnected.value.state.value.phase)
        assertTrue(fake.cancelledProcessIds.isEmpty())
        assertEquals(1, requests.size)
        finishDownload.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.DONE, reconnected.value.state.value.phase)
        assertEquals(1, publishes)
        assertEquals(1, releases)
        assertSnapshotClosed()
        reconnected.close(); otherOwner.close(); advanceUntilIdle()
        assertEquals(1, stops)
        assertEquals(1, creations)
    }

    @Test fun finalReleaseCancelsDownloadAndClosesReplayAndForegroundOnce() = runTest {
        val sessions = sessions(this)
        val first = sessions.attach()
        val second = sessions.attach()
        first.value.initialize(); advanceUntilIdle()
        choose(first.value); advanceUntilIdle()
        first.value.download(); runCurrent()
        first.close(); first.close()
        assertTrue(fake.cancelledProcessIds.isEmpty())
        second.close(); second.close(); advanceUntilIdle()
        assertEquals(listOf("task-1"), fake.cancelledProcessIds)
        assertEquals(1, stops)
        assertEquals(1, releases)
        assertEquals(0, publishes)
        assertFalse(second.scope.coroutineContext[Job]!!.isActive)
        assertSnapshotClosed()
    }

    @Test fun finalReleaseDiscardsChooserAndOldAttachmentCannotCloseNewSession() = runTest {
        val sessions = sessions(this)
        val old = sessions.attach()
        old.value.initialize(); advanceUntilIdle()
        choose(old.value); advanceUntilIdle()
        assertEquals(HomePhase.CHOOSING, old.value.state.value.phase)
        old.close(); advanceUntilIdle()
        assertSnapshotClosed()
        val next = sessions.attach()
        assertNotSame(old.value, next.value)
        old.close()
        assertEquals(1, stops)
        assertTrue(next.scope.coroutineContext[Job]!!.isActive)
        next.value.initialize(); advanceUntilIdle()
        assertEquals(HomePhase.READY, next.value.state.value.phase)
        assertEquals(2, creations)
        next.close(); advanceUntilIdle()
        assertEquals(2, stops)
    }

    @Test fun callerScopeCancellationDoesNotCancelSessionButFinalReleaseDoes() = runTest {
        val callerJob = Job()
        val sessions = sessions(CoroutineScope(coroutineContext + callerJob))
        val attachment = sessions.attach()
        attachment.value.initialize(); advanceUntilIdle()
        choose(attachment.value); advanceUntilIdle()
        attachment.value.download(); runCurrent()
        var auxiliaryCleanups = 0
        attachment.scope.launch {
            try { awaitCancellation() } finally { auxiliaryCleanups++ }
        }
        runCurrent()
        callerJob.cancel(); advanceUntilIdle()
        assertEquals(HomePhase.DOWNLOADING, attachment.value.state.value.phase)
        assertTrue(fake.cancelledProcessIds.isEmpty())
        assertEquals(0, auxiliaryCleanups)
        attachment.close(); advanceUntilIdle()
        assertEquals(1, auxiliaryCleanups)
        assertEquals(listOf("task-1"), fake.cancelledProcessIds)
        assertSnapshotClosed()
    }

    @Test fun finalReleaseWhileAdmissionIsPendingNeverStartsEngine() = runTest {
        admission = CompletableDeferred()
        val sessions = sessions(this)
        val attachment = sessions.attach()
        attachment.value.initialize(); advanceUntilIdle()
        choose(attachment.value); advanceUntilIdle()
        attachment.value.download()
        attachment.close(); advanceUntilIdle()
        admission!!.complete(Unit); advanceUntilIdle()
        assertTrue(requests.isEmpty())
        assertEquals(0, publishes)
        assertEquals(0, releases)
        assertSnapshotClosed()
    }

    @Test fun factoryFailureCancelsItsScopeAndAllowsFreshAttachment() = runTest {
        val createdScopes = mutableListOf<CoroutineScope>()
        val sessions = SharedSessionOwner(coroutineContext, create = { owned ->
            createdScopes += owned
            if (createdScopes.size == 1) throw IllegalStateException("synthetic factory failure")
            "session"
        }, stop = { stops++ })
        assertThrows(IllegalStateException::class.java) { sessions.attach() }
        assertFalse(createdScopes.first().coroutineContext[Job]!!.isActive)
        val attachment = sessions.attach()
        assertEquals("session", attachment.value)
        assertTrue(attachment.scope.coroutineContext[Job]!!.isActive)
        attachment.close(); advanceUntilIdle()
        assertEquals(1, stops)
    }
}
