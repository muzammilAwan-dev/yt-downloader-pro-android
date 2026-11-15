package io.github.muzammilawan.clipharbor.download

import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.SharedSessionOwner
import io.github.muzammilawan.clipharbor.feature.home.HomeController
import io.github.muzammilawan.clipharbor.feature.home.HomePhase
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
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
class ForegroundAdmissionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=foreground"

    private inner class Harness(scope: CoroutineScope) {
        val fake = FakeEngine()
        val events = mutableListOf<String>()
        val starts = mutableListOf<String>()
        val dispatches = ConcurrentLinkedQueue<() -> Unit>()
        val finish = CompletableDeferred<Unit>()
        var cleanup: CompletableDeferred<Unit>? = null
        var publication: CompletableDeferred<Unit>? = null
        var rejectStart = false
        var rejectNativeCancel = false
        var downloads = 0
        var publishes = 0
        var retains = 0
        var releases = 0
        var serial = 0
        var callback: ((ProgressEvent) -> Unit)? = null
        val snapshots = mutableListOf<ResolvedSnapshot>()
        val engine = object : Engine by fake {
            override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
                val snapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}")
                snapshots += snapshot
                it.copy(resolvedSnapshot = snapshot)
            }
            override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
                onLine: (String) -> Unit): Result<DownloadOutcome> {
                downloads++
                events += "engine"
                callback = onProgress
                try { finish.await() } finally {
                    cleanup?.let { gate -> withContext(NonCancellable) { withTimeout(5_000) { gate.await() } } }
                }
                request.outputDirectory.mkdirs()
                val file = File(request.outputDirectory, "synthetic.mp4").apply { writeText("synthetic media") }
                return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList()))
            }
            override fun cancel(processId: String): Boolean {
                val result = fake.cancel(processId)
                if (rejectNativeCancel) throw IllegalStateException("Cookie: cancel-secret")
                return result
            }
        }
        val admission = ForegroundAdmission(retain = { task ->
            val attached = sessions.attach()
            retains++
            object : ForegroundAdmission.Owner {
                override fun timeout() { attached.value.onForegroundTimeout(task) {} }
                override fun close() { releases++; attached.close() }
            }
        }, start = { task ->
            starts += task
            if (rejectStart) throw IllegalStateException("Cookie: start-secret")
        }, stopPending = { events += "stopPending:$it" }, dispatch = { dispatches.add(it) }, admissionTimeoutMs = 1_000)
        val sessions: SharedSessionOwner<HomeController> = SharedSessionOwner(scope.coroutineContext, create = { owned ->
            HomeController(engine, owned, temporary.newFolder(), publish = { file ->
                publishes++
                publication?.await()
                PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4")
            }, newTaskId = { "task-${++serial}" }, foreground = admission)
        }, stop = HomeController::close)
        val ui = sessions.attach()
        val home get() = ui.value

        fun choose() { home.editUrl(url); home.resolve() }
        fun flush() { while (true) (dispatches.poll() ?: break).invoke() }
        fun close() { ui.close(); flush() }
        fun assertSnapshotClosed() {
            assertThrows(IllegalStateException::class.java) {
                snapshots.first().withFile(url, "task-1", temporary.newFolder()) { error("must not run") }
            }
        }
    }

    private class Host(private val events: MutableList<String>, private val reject: Boolean = false) : ForegroundAdmission.Host {
        var promotions = 0
        var stops = 0
        val notices = mutableListOf<String>()
        override fun promote() {
            promotions++
            events += "promote"
            if (reject) throw SecurityException("Cookie: promotion-secret")
        }
        override fun progress(percent: Int) { notices += "progress:$percent" }
        override fun publishing() { notices += "publishing" }
        override fun stop() { if (stops == 0) { stops++; events += "stop" } }
    }

    @Test fun successfulPromotionPrecedesEngineAndRetainsSessionAcrossUiReconnection() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download(); runCurrent()
        assertEquals(listOf("task-1"), h.starts)
        assertEquals(0, h.downloads)
        h.ui.close()
        val reconnected = h.sessions.attach()
        assertSame(h.home, reconnected.value)
        val host = Host(h.events)
        assertTrue(h.admission.serviceStarted("task-1", host))
        runCurrent()
        assertEquals(listOf("promote", "engine"), h.events)
        h.finish.complete(Unit); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.DONE, reconnected.value.state.value.phase)
        assertEquals(1, h.publishes)
        assertEquals(1, h.retains)
        assertEquals(1, h.releases)
        assertEquals(1, host.stops)
        h.assertSnapshotClosed()
        reconnected.close(); h.close(); advanceUntilIdle()
    }

    @Test fun startRejectionNeverRunsEngineAndClosesAdmissionAndReplay() = runTest {
        val h = Harness(this)
        h.rejectStart = true
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download(); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.FAILED, h.home.state.value.phase)
        assertEquals(0, h.downloads)
        assertEquals(0, h.publishes)
        assertEquals(1, h.releases)
        assertEquals(listOf("stopPending:task-1"), h.events)
        assertFalse(h.home.state.value.status.contains("secret"))
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun promotionRejectionStopsPartialServiceAndNeverRunsEngine() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download(); runCurrent()
        val host = Host(h.events, reject = true)
        assertFalse(h.admission.serviceStarted("task-1", host))
        advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.FAILED, h.home.state.value.phase)
        assertEquals(0, h.downloads)
        assertEquals(1, host.stops)
        assertEquals(1, h.releases)
        assertFalse(h.home.state.value.status.contains("secret"))
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun cancellationBeforePromotionHandlesLateStartWithoutDownloadingOrLeakingOwner() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        h.home.cancel(); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.CANCELED, h.home.state.value.phase)
        val late = Host(h.events)
        assertFalse(h.admission.serviceStarted("task-1", late))
        assertEquals(1, late.promotions) // A delivered startForegroundService still needs prompt promotion.
        assertEquals(1, late.stops)
        assertEquals(0, h.downloads)
        assertEquals(1, h.releases)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun cancellationAfterPromotionButBeforeAwaitResumesReleasesTheService() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        assertTrue(h.admission.serviceStarted("task-1", host))
        h.home.cancel(); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.CANCELED, h.home.state.value.phase)
        assertEquals(0, h.downloads)
        assertEquals(1, host.stops)
        assertEquals(1, h.releases)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun admissionDeadlineFailsClosedAndLateServiceDoesNotReviveTask() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download(); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.FAILED, h.home.state.value.phase)
        assertEquals(0, h.downloads)
        assertEquals(1, h.releases)
        val late = Host(h.events)
        assertFalse(h.admission.serviceStarted("task-1", late))
        assertEquals(1, late.stops)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun serviceLossCancelsTheActualDownloadJobNotTheCompletedAdmissionWait() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        assertEquals(1, h.downloads)
        h.admission.serviceLost(host); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.CANCELED, h.home.state.value.phase)
        assertEquals(0, h.publishes)
        assertEquals(1, h.releases)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun timeoutStopsImmediatelyButRetainsSessionUntilNativeCleanupEnds() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        h.cleanup = CompletableDeferred()
        h.admission.timedOut(host) { host.stop() }
        h.admission.serviceLost(host) // onDestroy after timeout must not overwrite the pause reason.
        runCurrent(); h.flush()
        assertEquals(1, host.stops)
        assertEquals(HomePhase.PAUSING, h.home.state.value.phase)
        assertEquals(0, h.releases)
        h.cleanup!!.complete(Unit); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.PAUSED, h.home.state.value.phase)
        assertEquals(ErrorCode.FGS_TIMEOUT, h.home.state.value.pauseReason)
        assertEquals(1, h.releases)
        assertEquals(0, h.publishes)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun oldServiceCallbacksCannotCancelOrStopNewlyPromotedTask() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val old = Host(h.events)
        h.admission.serviceStarted("task-1", old); runCurrent()
        h.home.cancel(); advanceUntilIdle(); h.flush()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val current = Host(h.events)
        h.admission.serviceStarted("task-2", current); runCurrent()
        val stale = Host(h.events)
        assertFalse(h.admission.serviceStarted("task-1", stale))
        h.admission.serviceLost(old)
        h.admission.timedOut(old) { old.stop() }
        assertEquals(HomePhase.DOWNLOADING, h.home.state.value.phase)
        assertEquals(2, h.downloads)
        assertEquals(0, current.stops)
        assertEquals(0, stale.promotions)
        h.home.cancel(); advanceUntilIdle(); h.flush(); h.close()
    }

    @Test fun progressFromWorkerThreadsIsBoundedCoalescedAndIgnoredAfterClose() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        Thread {
            for (percent in 0..100) h.callback!!(ProgressEvent(percent.toFloat(), 0, "Cookie: secret"))
            h.callback!!(ProgressEvent(19.9f, 0, "Cookie: secret"))
        }.apply { start(); join() }
        assertTrue(host.notices.isEmpty())
        assertEquals(1, h.dispatches.size)
        h.flush()
        assertEquals(listOf("progress:19"), host.notices)
        h.home.cancel(); advanceUntilIdle(); h.flush()
        h.callback!!(ProgressEvent(88f, 0, "late")); h.flush()
        assertEquals(listOf("progress:19"), host.notices)
        assertEquals(1, host.stops)
        h.close()
    }

    @Test fun arbitraryUrlsCannotBecomeServiceIdentifiers() = runTest {
        val h = Harness(this)
        val error = runCatching { h.admission.acquire(url) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, h.retains)
        assertTrue(h.starts.isEmpty())
        h.close()
    }

    @Test fun failedAdmissionRetainsLastOwnerUntilHomeHasClosedTheReplay() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        h.ui.close()
        advanceUntilIdle()
        assertEquals(0, h.releases)
        assertEquals(HomePhase.FAILED, h.home.state.value.phase)
        h.assertSnapshotClosed()
        h.flush()
        assertEquals(1, h.releases)
        h.close()
    }

    @Test fun terminalPublicationDoesNotResurrectAClosedNotification() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        h.callback!!(ProgressEvent(7f, 0, "Cookie: private"))
        h.finish.complete(Unit); advanceUntilIdle()
        // Home has closed the lease: queued updates cannot resurrect the terminal notification.
        h.flush()
        assertTrue(host.notices.isEmpty())
        assertEquals(1, host.stops)
        assertEquals(1, h.releases)
        h.close()
    }

    @Test fun savingNoticeReplacesProgressAndServiceLossCancelsPublicationWithoutClaimingSave() = runTest {
        val h = Harness(this)
        h.publication = CompletableDeferred()
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        h.callback!!(ProgressEvent(7f, 0, "Cookie: private"))
        h.finish.complete(Unit); runCurrent(); h.flush()
        assertEquals(listOf("publishing"), host.notices)
        assertEquals(HomePhase.PUBLISHING, h.home.state.value.phase)
        h.admission.serviceLost(host); advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.CANCELED, h.home.state.value.phase)
        assertNull(h.home.state.value.saved)
        assertEquals(1, h.releases)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun duplicateDeliveredStartDoesNotReplaceThePromotedHost() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        assertTrue(h.admission.serviceStarted("task-1", host))
        assertTrue(h.admission.serviceStarted("task-1", host))
        val duplicate = Host(h.events)
        assertFalse(h.admission.serviceStarted("task-1", duplicate))
        runCurrent()
        assertEquals(1, host.promotions)
        assertEquals(0, host.stops)
        assertEquals(1, duplicate.stops)
        h.admission.serviceLost(duplicate)
        assertEquals(HomePhase.DOWNLOADING, h.home.state.value.phase)
        h.home.cancel(); advanceUntilIdle(); h.flush(); h.close()
    }

    @Test fun oldQueuedReleaseCannotStopPendingNewAdmission() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        h.home.cancel(); runCurrent() // Do not flush the old pending-start release yet.
        h.choose(); runCurrent()
        h.home.download()
        h.flush()
        assertEquals(listOf("task-1", "task-2"), h.starts)
        assertFalse(h.events.any { it.startsWith("stopPending:") })
        val host = Host(h.events)
        assertTrue(h.admission.serviceStarted("task-2", host))
        runCurrent()
        assertEquals(1, h.downloads)
        h.home.cancel(); advanceUntilIdle(); h.flush(); h.close()
    }

    @Test fun timeoutNativeCancelFailureStillCancelsActualJobAndPreservesPauseReason() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        val host = Host(h.events)
        h.admission.serviceStarted("task-1", host); runCurrent()
        h.rejectNativeCancel = true
        assertThrows(IllegalStateException::class.java) { h.admission.timedOut(host, host::stop) }
        h.admission.serviceLost(host)
        advanceUntilIdle(); h.flush()
        assertEquals(HomePhase.PAUSED, h.home.state.value.phase)
        assertEquals(ErrorCode.FGS_TIMEOUT, h.home.state.value.pauseReason)
        assertEquals(1, host.stops)
        assertEquals(1, h.releases)
        h.assertSnapshotClosed()
        h.close()
    }

    @Test fun leaseRejectsOutOfBoundsProgressAndCloseIsIdempotent() = runTest {
        val queue = ConcurrentLinkedQueue<() -> Unit>()
        val host = Host(mutableListOf())
        var releases = 0
        lateinit var admission: ForegroundAdmission
        admission = ForegroundAdmission(retain = {
            object : ForegroundAdmission.Owner {
                override fun timeout() {}
                override fun close() { releases++ }
            }
        }, start = { admission.serviceStarted(it, host) }, stopPending = { error("promoted") },
            dispatch = { queue.add(it) })
        val lease = admission.acquire("task-1")
        lease.progress(-1); lease.progress(101)
        assertTrue(queue.isEmpty())
        lease.progress(12); lease.progress(100)
        checkNotNull(queue.poll()).invoke()
        assertEquals(listOf("progress:100"), host.notices)
        lease.publishing()
        checkNotNull(queue.poll()).invoke()
        assertEquals(listOf("progress:100", "publishing"), host.notices)
        lease.close(); lease.close(); lease.progress(15); lease.publishing()
        assertEquals(1, queue.size)
        checkNotNull(queue.poll()).invoke()
        assertEquals(1, releases)
        assertEquals(1, host.stops)
    }

    @Test fun staleDeliveredStartDuringNewAdmissionIsPromotedThenStoppedWithoutAdmittingOldWork() = runTest {
        val h = Harness(this)
        h.home.initialize(); advanceUntilIdle()
        h.choose(); advanceUntilIdle()
        h.home.download()
        h.home.cancel(); runCurrent()
        h.choose(); runCurrent()
        h.home.download()
        val stale = Host(h.events)
        assertFalse(h.admission.serviceStarted("task-1", stale))
        assertEquals(1, stale.promotions)
        assertEquals(1, stale.stops)
        assertEquals(0, h.downloads)
        h.flush()
        val current = Host(h.events)
        assertTrue(h.admission.serviceStarted("task-2", current))
        runCurrent()
        assertEquals(1, h.downloads)
        h.home.cancel(); advanceUntilIdle(); h.flush(); h.close()
    }
}
