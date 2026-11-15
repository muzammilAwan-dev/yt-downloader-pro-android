package io.github.muzammilawan.clipharbor.download

import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-task, process-local promotion handshake. Acquire/service callbacks run on Android main.
 * [dispatch] must enqueue (never inline) on that same thread, including when called by a worker.
 * The retained session outlives the UI and is released only after Home's terminal cleanup returns.
 * No media metadata or engine output crosses this boundary; no service callback launches work.
 */
class ForegroundAdmission(
    private val retain: (String) -> Owner,
    private val start: (String) -> Unit,
    private val stopPending: (String) -> Unit,
    private val dispatch: (() -> Unit) -> Unit,
    private val admissionTimeoutMs: Long = 4_000,
) : DownloadForeground {
    init { require(admissionTimeoutMs > 0) }

    interface Owner {
        fun timeout()
        fun cancel()
        fun close()
    }
    interface Host {
        fun promote()
        fun progress(percent: Int)
        fun publishing()
        fun cancelling() {}
        fun stop()
    }
    private var current: Admission? = null

    /** User Cancel cannot cross publication; stale/repeated actions never touch newer work. */
    fun cancel(taskId: String?): Boolean {
        val record = current ?: return false
        if (record.task != taskId || record.host == null || !record.open.get() || !record.job.isActive ||
            record.publishing.get() || record.timedOut || record.cancelRequested) return false
        record.cancelRequested = true
        try { record.host?.cancelling() } catch (_: Exception) { /* Optional notification refresh. */ }
        try { record.owner.cancel() } finally { record.job.cancel() }
        return true
    }

    override suspend fun acquire(taskId: String): DownloadForeground.Lease {
        require(taskId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid task identifier" }
        check(current?.open?.get() != true) { "Foreground task already active" }
        // Capture the caller Job BEFORE withTimeoutOrNull creates its child admission coroutine.
        val record = Admission(taskId, retain(taskId), currentCoroutineContext().job)
        current = record
        try {
            currentCoroutineContext().ensureActive()
            try { start(taskId) } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                throw unavailable()
            }
            val promoted = withTimeoutOrNull(admissionTimeoutMs) { record.ready.await(); true } ?: false
            if (!promoted) throw unavailable()
            currentCoroutineContext().ensureActive()
            return record
        } catch (error: Throwable) {
            record.close()
            throw error
        }
    }

    /** Every delivered foreground start must either be promoted promptly or safely stopped. */
    fun serviceStarted(taskId: String?, host: Host): Boolean {
        val record = current
        if (record?.open?.get() == true && record.task != taskId && record.host != null) {
            host.stop() // Do not replace a newer task's notification with a stale one.
            return false
        }
        if (record?.host === host && record.open.get()) return true
        if (record?.open?.get() == true && record.host != null) {
            host.stop()
            return false
        }
        try { host.promote() } catch (error: Exception) {
            host.stop()
            if (record?.open?.get() == true && record.task == taskId) {
                record.ready.completeExceptionally(unavailable())
            }
            return false
        }
        if (record == null || !record.open.get() || record.task != taskId) {
            host.stop()
            return false
        }
        record.host = host
        record.ready.complete(Unit)
        return true
    }

    /** Losing foreground protection interrupts the actual task; release still waits for cleanup. */
    fun serviceLost(host: Host) {
        val record = current ?: return
        if (record.host === host && record.open.get() && !record.timedOut) record.job.cancel()
    }

    fun timedOut(host: Host, stop: () -> Unit) {
        try { stop() } finally {
            val record = current
            if (record?.host === host && record.open.get() && !record.timedOut && !record.cancelRequested) {
                record.timedOut = true
                try { record.owner.timeout() } finally { record.job.cancel() }
            }
        }
    }

    private fun unavailable() = EngineFailure(DownloadError(ErrorCode.UNAVAILABLE, Stage.DOWNLOADING,
        detail = "Foreground service admission failed. Find formats again to make a new attempt."))

    private inner class Admission(val task: String, val owner: Owner, val job: Job) : DownloadForeground.Lease {
        val ready = CompletableDeferred<Unit>()
        val open = AtomicBoolean(true)
        var host: Host? = null // Main-thread only.
        var timedOut = false
        var cancelRequested = false
        val publishing = AtomicBoolean(false)
        private val latest = AtomicReference<Int?>(null)
        private val posted = AtomicBoolean(false)

        override fun progress(percent: Int) {
            if (percent in 0..100 && !publishing.get()) update(percent)
        }

        override fun publishing() { publishing.set(true); update(-1) }

        private fun update(value: Int) {
            if (!open.get()) return
            latest.set(value)
            if (!posted.compareAndSet(false, true)) return
            try {
                dispatch {
                    posted.set(false)
                    val notice = latest.getAndSet(null)
                    if (open.get() && !timedOut && !cancelRequested && current === this && notice != null) {
                        try {
                            if (publishing.get()) host?.publishing() else host?.progress(notice)
                        } catch (_: Exception) { /* Notification refresh must never fail the download. */ }
                    }
                }
            } catch (_: Exception) { posted.set(false) }
        }

        override fun close() {
            if (!open.compareAndSet(true, false)) return
            latest.set(null)
            dispatch {
                // A previous task's queued release cannot clear a newer admission.
                if (current === this) current = null
                try {
                    val service = host
                    if (service != null) service.stop()
                    else if (current == null) stopPending(task)
                } finally {
                    ready.cancel()
                    owner.close()
                }
            }
        }
    }
}
