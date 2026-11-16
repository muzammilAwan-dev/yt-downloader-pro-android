package io.github.muzammilawan.clipharbor.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ErrorRecoveryTest {
    @Test fun everyErrorRecoveryAndRetryFlagExactlyMatchTheCheckedInContract() {
        val contract = sequenceOf(File("contracts/errors.json"), File("../contracts/errors.json")).first { it.isFile }.readText()
        val rows = Regex("\\{[^{}]*\"code\"\\s*:\\s*\"([A-Z0-9_]+)\"[^{}]*\"recovery\"\\s*:\\s*\"([A-Z0-9_]+)\"[^{}]*\"retryable\"\\s*:\\s*(true|false)[^{}]*}")
            .findAll(contract).toList()
        assertEquals(ErrorCode.entries.size, rows.size)
        for (row in rows) {
            val code = ErrorCode.valueOf(row.groupValues[1])
            assertEquals(code.name, row.groupValues[2], ErrorRecovery.forCode(code).contractAction)
            assertEquals(code.retryable, row.groupValues[3].toBoolean())
        }
    }

    @Test fun onlyImplementedSafeRecoveryOffersFreshResolveAndNoUnsupportedActionPretendsToExist() {
        assertTrue(ErrorRecovery.forCode(ErrorCode.HTTP_403_ON_MEDIA).freshResolve)
        assertTrue(ErrorRecovery.forCode(ErrorCode.NO_SPACE).freshResolve)
        for (code in listOf(ErrorCode.LOGIN_REQUIRED, ErrorCode.ENGINE_OUTDATED, ErrorCode.JS_RUNTIME_MISSING,
            ErrorCode.FORMATS_WITHHELD, ErrorCode.GEO_BLOCKED, ErrorCode.UNAVAILABLE, ErrorCode.MERGE_FAILED,
            ErrorCode.POST_PROCESS_FAILED, ErrorCode.STORAGE_PERMISSION_LOST)) assertFalse(code.name, ErrorRecovery.forCode(code).freshResolve)
        assertTrue(ErrorRecovery.forCode(ErrorCode.NETWORK_OFFLINE).explanation.contains("not automatic"))
        assertTrue(ErrorRecovery.forCode(ErrorCode.ENGINE_OUTDATED).explanation.contains("APK"))
        assertEquals(5_000L, ErrorRecovery.forCode(ErrorCode.NETWORK_TIMEOUT).waitMs)
    }
}
