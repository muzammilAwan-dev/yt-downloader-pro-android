package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundTimeoutTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=timeout"
    private val fake = FakeEngine()
    private var serial = 0
    private val events = mutableListOf<String>()
    private val logged = mutableListOf<Pair<String, Map<String, String>>>()
    private val failures = mutableListOf<DownloadError>()
    private val snapshots = mutableListOf<ResolvedSnapshot>()
    private val progress = mutableListOf<(ProgressEvent) -> Unit>()
    private val finishDownload = CompletableDeferred<Unit>()
    private val finishPublish = CompletableDeferred<Unit>()
    private var admissionGate: CompletableDeferred<Unit>? = null
    private var cleanupGate: CompletableDeferred<Unit>? = null
    private var holdPublish = false
    private var failCleanup = false
    private var failCancel = false
    private var downloads = 0
    private var publishes = 0
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            val snapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}")
            snapshots += snapshot
            it.copy(resolvedSnapshot = snapshot)
        }

        override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit): Result<DownloadOutcome> {
            downloads++
            progress += onProgress
            try { finishDownload.await() } finally {
                cleanupGate?.let { gate -> withContext(NonCancellable) { withTimeout(5_000) { gate.await() } } }
                if (failCleanup) throw EngineFailure(DownloadError(ErrorCode.NO_SPACE, Stage.DOWNLOADING,
                    detail = "Cookie: private-cleanup-secret"))
            }
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "synthetic.mp4").apply { writeText("synthetic bytes") }
            return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList()))
        }

        override fun cancel(processId: String): Boolean {
            events += "cancel:$processId"
            val canceled = fake.cancel(processId)
            if (failCancel) throw IllegalStateException("synthetic cancellation failure")
            return canceled
        }
    }

    private fun controller(scope: CoroutineScope) = HomeController(engine, scope, temporary.newFolder(), publish = { file ->
        publishes++
        if (holdPublish) try { finishPublish.await() } finally {
            cleanupGate?.let { gate -> withContext(NonCancellable) { withTimeout(5_000) { gate.await() } } }
        }
        PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4")
    }, observer = object : HomeObserver {
        override fun event(task: String?, stage: String, event: String, fields: Map<String, String>) {
            logged += event to fields
        }
        override fun failure(task: String?, error: DownloadError) { failures += error }
    }, newTaskId = { "task-${++serial}" }, foreground = object : DownloadForeground {
        override suspend fun acquire(taskId: String): DownloadForeground.Lease {
            admissionGate?.await()
            return object : DownloadForeground.Lease {
                override fun progress(percent: Int) { events += "progress:$percent" }
                override fun publishing() { events += "publishing" }
                override fun close() { events += "release:$taskId" }
            }
        }
    })

    private fun assertSnapshotClosed(index: Int = 0) {
        assertThrows(IllegalStateException::class.java) {
            snapshots[index].withFile(url, "task-${index + 1}", temporary.newFolder()) { error("must not run") }
        }
    }

    @Test fun timeoutStopsServiceFirstAndKeepsHomeBusyUntilWorkerCleanupEnds() = runTest {
        cleanupGate = CompletableDeferred()
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertTrue(home.onForegroundTimeout("task-1") { events += "stopService" })
        assertEquals(listOf("stopService", "cancel:task-1"), events)
        runCurrent()
        assertEquals(HomePhase.PAUSING, home.state.value.phase)
        assertEquals(ErrorCode.FGS_TIMEOUT, home.state.value.pauseReason)
        assertTrue(home.state.value.busy)
        home.editUrl("https://example.invalid/replacement"); home.resolve(); home.download(); home.cancel()
        home.receiveShared("https://example.invalid/pending")
        home.usePendingLink()
        progress.single()(ProgressEvent(99f, 0, "Cookie: secret"))
        assertEquals(url, home.state.value.url)
        assertEquals("https://example.invalid/pending", home.state.value.pendingUrl)
        assertNull(home.state.value.progress)
        assertEquals(1, fake.resolveCalls)
        assertEquals(1, downloads)
        assertEquals(0, publishes)
        assertFalse(events.any { it.startsWith("release:") })
        cleanupGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertFalse(home.state.value.busy)
        assertEquals(ErrorCode.FGS_TIMEOUT, home.state.value.pauseReason)
        assertEquals(1, events.count { it == "release:task-1" })
        assertTrue(failures.isEmpty()) // This is a pause, not an ordinary failure or user cancel.
        assertFalse(logged.any { it.first == "canceled" })
        val pause = logged.single { it.first == "paused" }.second
        assertEquals("FGS_TIMEOUT", pause["code"])
        assertEquals("PAUSE_AND_RESCHEDULE", pause["recovery"])
        assertEquals("false", pause["rescheduled"])
        assertTrue(home.state.value.status.contains("not available"))
        assertSnapshotClosed()
        home.close()
    }

    @Test fun timeoutBeforeAdmissionClosesReplayWithoutStartingEngine() = runTest {
        admissionGate = CompletableDeferred()
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download()
        assertTrue(home.onForegroundTimeout("task-1") { events += "stopService" })
        advanceUntilIdle()
        admissionGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertEquals(0, downloads)
        assertEquals(0, publishes)
        assertEquals(listOf("stopService", "cancel:task-1"), events)
        assertSnapshotClosed()
        home.close()
    }

    @Test fun platformTimeoutCanInterruptPublicationButNeverReportsAnUncommittedSave() = runTest {
        holdPublish = true
        finishDownload.complete(Unit)
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertEquals(HomePhase.PUBLISHING, home.state.value.phase)
        cleanupGate = CompletableDeferred()
        assertTrue(home.onForegroundTimeout("task-1") { events += "stopService" })
        runCurrent()
        assertEquals(HomePhase.PAUSING, home.state.value.phase)
        assertNull(home.state.value.saved)
        cleanupGate!!.complete(Unit); advanceUntilIdle()
        finishPublish.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertNull(home.state.value.saved)
        assertEquals(1, events.count { it == "release:task-1" })
        assertEquals(1, publishes)
        assertSnapshotClosed()
        home.close()
    }

    @Test fun staleTimeoutStopsItsServiceButCannotCancelChooserOrFinishedTask() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        assertEquals(HomePhase.CHOOSING, home.state.value.phase)
        home.download(); runCurrent()
        assertFalse(home.onForegroundTimeout("old-task") { events += "stopService" })
        assertEquals(HomePhase.DOWNLOADING, home.state.value.phase)
        finishDownload.complete(Unit); advanceUntilIdle()
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        assertEquals(HomePhase.DONE, home.state.value.phase)
        assertTrue(fake.cancelledProcessIds.isEmpty())
        assertNull(home.state.value.pauseReason)
        home.close()
    }

    @Test fun repeatedTimeoutAndSessionCloseDoNotRepeatNativeCancelOrRelease() = runTest {
        cleanupGate = CompletableDeferred()
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertTrue(home.onForegroundTimeout("task-1") { events += "stopService" })
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        home.close(); home.close(); runCurrent()
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        cleanupGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("task-1"), fake.cancelledProcessIds)
        assertEquals(1, events.count { it == "release:task-1" })
        assertEquals(3, events.count { it == "stopService" })
        assertSnapshotClosed()
    }

    @Test fun retryRequiresFreshResolveAndConfirmationAndIgnoresOldTimeoutOrProgress() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        home.onForegroundTimeout("task-1") { events += "stopService" }; advanceUntilIdle()
        home.download(); advanceUntilIdle()
        assertEquals(1, downloads)
        home.resolve(); advanceUntilIdle()
        assertEquals(2, fake.resolveCalls)
        assertEquals(HomePhase.CHOOSING, home.state.value.phase)
        assertNull(home.state.value.pauseReason)
        assertEquals(1, downloads)
        home.download(); runCurrent()
        assertEquals(2, downloads)
        assertFalse(home.onForegroundTimeout("task-1") { events += "stopService" })
        progress.first()(ProgressEvent(99f, 0, "old"))
        assertNull(home.state.value.progress)
        progress.last()(ProgressEvent(15f, 0, "new"))
        assertEquals(15, home.state.value.progress)
        home.close(); advanceUntilIdle()
        assertSnapshotClosed(0); assertSnapshotClosed(1)
    }

    @Test fun stopServiceExceptionStillInterruptsTaskAndClosesPrivateInput() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertThrows(IllegalStateException::class.java) {
            home.onForegroundTimeout("task-1") {
                events += "stopService"
                throw IllegalStateException("synthetic stop failure")
            }
        }
        advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertEquals(listOf("stopService", "cancel:task-1", "release:task-1"), events)
        assertSnapshotClosed()
        home.close()
    }

    @Test fun nativeCancelExceptionCannotPreventCoroutineCleanup() = runTest {
        failCancel = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertThrows(IllegalStateException::class.java) {
            home.onForegroundTimeout("task-1") { events += "stopService" }
        }
        advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertEquals(listOf("stopService", "cancel:task-1", "release:task-1"), events)
        assertSnapshotClosed()
        home.close()
    }

    @Test fun cleanupFailureCannotOverwriteForegroundTimeoutOrExposeItsSecrets() = runTest {
        failCleanup = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertTrue(home.onForegroundTimeout("task-1") { events += "stopService" })
        advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertEquals(ErrorCode.FGS_TIMEOUT, home.state.value.pauseReason)
        assertTrue(failures.isEmpty())
        assertFalse(home.state.value.status.contains("secret"))
        assertEquals(1, logged.count { it.first == "paused" })
        assertSnapshotClosed()
        home.close()
    }
}
