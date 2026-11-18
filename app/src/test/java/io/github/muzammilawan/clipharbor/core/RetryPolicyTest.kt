package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test fun waitGrowsByPowersOfTwoFromFiveSecondsAndIsCapped() {
        val code = ErrorCode.NETWORK_TIMEOUT
        assertEquals(5_000L, RetryPolicy.waitMs(code, 1))
        assertEquals(10_000L, RetryPolicy.waitMs(code, 2))
        assertEquals(20_000L, RetryPolicy.waitMs(code, 3))
        assertEquals(40_000L, RetryPolicy.waitMs(code, 4))
        assertEquals(60_000L, RetryPolicy.waitMs(code, 5))
        assertEquals(60_000L, RetryPolicy.waitMs(code, 99))
        assertEquals(5_000L, RetryPolicy.waitMs(code, 0))
    }

    @Test fun futileFailuresNeverWaitAndNeverOfferARetry() {
        listOf(ErrorCode.AGE_OR_BOT_GATE, ErrorCode.LOGIN_REQUIRED, ErrorCode.GEO_BLOCKED,
            ErrorCode.UNAVAILABLE, ErrorCode.JS_RUNTIME_MISSING, ErrorCode.CANCELED).forEach { code ->
            assertFalse(code.name, RetryPolicy.permits(code))
            assertEquals(code.name, 0L, RetryPolicy.waitMs(code, 3))
            assertFalse(code.name, RetryPolicy.stillWaiting(code, 3, 0, 0))
        }
    }

    @Test fun unsupportedActionsInTheContractAreNotRetriedEither() {
        // MERGE_FAILED/POST_PROCESS_FAILED keep private parts; the contract offers no fresh resolve, so no wait.
        listOf(ErrorCode.MERGE_FAILED, ErrorCode.POST_PROCESS_FAILED, ErrorCode.STORAGE_PERMISSION_LOST,
            ErrorCode.ENGINE_OUTDATED).forEach { code ->
            assertFalse(code.name, RetryPolicy.permits(code))
            assertEquals(code.name, 0L, RetryPolicy.waitMs(code, 2))
        }
        listOf(ErrorCode.HTTP_403_ON_MEDIA, ErrorCode.NO_SPACE, ErrorCode.NETWORK_OFFLINE,
            ErrorCode.RESOLVE_TIMEOUT, ErrorCode.NETWORK_TIMEOUT, ErrorCode.FGS_TIMEOUT).forEach { code ->
            assertTrue(code.name, RetryPolicy.permits(code))
        }
        // Only transient conditions wait; a fixed storage or expired-media problem is retried immediately.
        assertEquals(0L, RetryPolicy.waitMs(ErrorCode.NO_SPACE, 3))
        assertEquals(0L, RetryPolicy.waitMs(ErrorCode.HTTP_403_ON_MEDIA, 3))
        assertTrue(RetryPolicy.waitMs(ErrorCode.NETWORK_OFFLINE, 2) > 0)
        assertTrue(RetryPolicy.waitMs(ErrorCode.FGS_TIMEOUT, 2) > 0)
    }

    @Test fun theGateOpensExactlyAtTheBackoffBoundaryAndTheLabelAdmitsTheRestart() {
        assertTrue(RetryPolicy.stillWaiting(ErrorCode.NETWORK_TIMEOUT, 2, 1_000, 1_000))
        assertTrue(RetryPolicy.stillWaiting(ErrorCode.NETWORK_TIMEOUT, 2, 1_000, 10_999))
        assertFalse(RetryPolicy.stillWaiting(ErrorCode.NETWORK_TIMEOUT, 2, 1_000, 11_000))
        assertTrue(RetryPolicy.retryLabel(1).contains("starts from the beginning"))
        assertTrue(RetryPolicy.retryLabel(RetryPolicy.MAX_CONSECUTIVE).startsWith("Retry ("))
        assertTrue(RetryPolicy.retryLabel(2).contains("2 of ${RetryPolicy.MAX_CONSECUTIVE}"))
    }
}
