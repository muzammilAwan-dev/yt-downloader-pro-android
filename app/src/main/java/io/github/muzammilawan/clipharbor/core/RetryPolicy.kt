package io.github.muzammilawan.clipharbor.core

/**
 * Whole-task retry budget (Phase 2B). Nothing here retries on its own: the app only gates how soon the user's
 * explicit retry (or queue review) may start again, and never offers a retry for failures where another attempt
 * cannot honestly help (bot gates, geo blocks, unavailable media, canceled work).
 *
 * yt-dlp's own request/fragment retries stay inside the engine and are counted separately — they never reach this
 * policy, so a single failed task is at most "engine retries inside the run" + "one wait per explicit user retry".
 */
object RetryPolicy {
    const val BASE_MS = 5_000L
    const val MAX_MS = 60_000L
    const val MAX_CONSECUTIVE = 5

    /** Codes where repeating the same request is known to be futile or dishonest. */
    val noRetryCodes = setOf(
        ErrorCode.AGE_OR_BOT_GATE,
        ErrorCode.LOGIN_REQUIRED,
        ErrorCode.GEO_BLOCKED,
        ErrorCode.UNAVAILABLE,
        ErrorCode.JS_RUNTIME_MISSING,
        ErrorCode.CANCELED,
    )

    /**
     * Transient conditions where waiting helps. Codes the user fixes themselves (free space, expired media URL,
     * storage access) get an immediate retry instead — waiting there would only punish the fix.
     */
    val backoffCodes = setOf(
        ErrorCode.NETWORK_TIMEOUT,
        ErrorCode.RESOLVE_TIMEOUT,
        ErrorCode.NETWORK_OFFLINE,
        ErrorCode.FGS_TIMEOUT,
    )

    /** A retry may be offered only where the contract already allows a fresh resolve and nothing forbids it. */
    fun permits(code: ErrorCode): Boolean = ErrorRecovery.forCode(code).freshResolve && code !in noRetryCodes

    /**
     * Capped exponential backoff for the Nth consecutive explicit retry (1-based) of a transient failure. Attempts
     * beyond [MAX_CONSECUTIVE] keep the cap instead of growing forever. Other codes return 0 (retry immediately).
     */
    fun waitMs(code: ErrorCode, consecutiveFailures: Int): Long {
        if (!permits(code) || code !in backoffCodes) return 0L
        val attempt = consecutiveFailures.coerceIn(1, MAX_CONSECUTIVE)
        val doubled = BASE_MS shl (attempt - 1)
        return minOf(doubled, MAX_MS)
    }

    /** True when the retry button must still wait, given the failure time and the clock. */
    fun stillWaiting(code: ErrorCode, consecutiveFailures: Int, failedAtMs: Long, nowMs: Long): Boolean {
        if (!permits(code)) return false
        return nowMs - failedAtMs < waitMs(code, consecutiveFailures)
    }

    /** Honest label: every attempt starts from a fresh staging directory, so no partial file is continued. */
    fun retryLabel(consecutiveFailures: Int): String =
        if (consecutiveFailures >= MAX_CONSECUTIVE) "Retry (starts from the beginning)"
        else "Retry ${consecutiveFailures.coerceAtLeast(1)} of $MAX_CONSECUTIVE (starts from the beginning)"
}
