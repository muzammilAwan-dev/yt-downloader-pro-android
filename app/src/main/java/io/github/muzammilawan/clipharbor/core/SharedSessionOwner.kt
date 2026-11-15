package io.github.muzammilawan.clipharbor.core

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * In-process session ownership, not durable storage or foreground protection.
 * Attach/release and [stop] must run on the same thread (Android main); each attachment releases once.
 * The session owns its scope independently of the caller. Final release stops work, then cancels all
 * session coroutines; sensitive worker inputs still follow their existing worker-finally cleanup.
 */
class SharedSessionOwner<T>(
    private val context: CoroutineContext,
    private val create: (CoroutineScope) -> T,
    private val stop: (T) -> Unit,
) {
    private class Session<T>(val value: T, val scope: CoroutineScope, val job: CompletableJob) {
        var owners = 0
    }

    private var current: Session<T>? = null

    fun attach(): Attachment<T> {
        val session = current ?: run {
            val job = SupervisorJob()
            // Replace any Activity/caller Job rather than becoming its child.
            val scope = CoroutineScope(context + job)
            val value = try { create(scope) } catch (error: Throwable) {
                job.cancel()
                throw error
            }
            Session(value, scope, job).also { current = it }
        }
        session.owners++
        return Attachment(session.value, session.scope) {
            session.owners--
            if (session.owners == 0) {
                current = null
                try { stop(session.value) } finally { session.job.cancel() }
            }
        }
    }

    class Attachment<T> internal constructor(
        val value: T,
        val scope: CoroutineScope,
        private val release: () -> Unit,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            release()
        }
    }
}
