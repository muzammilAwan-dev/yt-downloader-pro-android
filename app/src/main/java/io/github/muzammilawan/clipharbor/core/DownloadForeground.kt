package io.github.muzammilawan.clipharbor.core

/**
 * Task-private foreground admission boundary. No URL, title, format plan or engine output crosses it.
 * An Android adapter must return only after service promotion succeeds, and clean up any partial start
 * when acquisition fails or is canceled. It must not launch/retry work itself.
 */
interface DownloadForeground {
    suspend fun acquire(taskId: String): Lease

    /** Notification updates may arrive on an engine thread. Implementations must be thread-safe,
     * non-blocking and non-throwing, and ignore updates after close. Close must be idempotent.
     */
    interface Lease {
        fun progress(percent: Int)
        fun publishing()
        fun close()
    }

    /** Current preview only: explicitly provides no background execution protection. */
    object ActivityOnly : DownloadForeground {
        private val lease = object : Lease {
            override fun progress(percent: Int) {}
            override fun publishing() {}
            override fun close() {}
        }
        override suspend fun acquire(taskId: String): Lease = lease
    }
}
