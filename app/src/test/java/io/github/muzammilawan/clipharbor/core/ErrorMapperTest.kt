package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {
    @Test
    fun missingJsRuntimeIsItsOwnCode() {
        val error = ErrorMapper.fromExit(
            Stage.RESOLVING,
            1,
            "ERROR: [youtube] dQw4w9WgXcQ: No supported JavaScript runtime. Some formats may be missing.",
        )
        assertEquals(ErrorCode.JS_RUNTIME_MISSING, error.code)
        assertEquals(false, error.retryable)
    }

    @Test
    fun http403OnMediaIsRetryableAfterReResolve() {
        val error = ErrorMapper.fromExit(
            Stage.DOWNLOADING,
            1,
            "ERROR: unable to download video data: HTTP Error 403: Forbidden",
        )
        assertEquals(ErrorCode.HTTP_403_ON_MEDIA, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun mergeStageFailureMapsToMergeFailed() {
        val error = ErrorMapper.fromExit(Stage.MERGING, 1, "ERROR: ffmpeg exited with code 1")
        assertEquals(ErrorCode.MERGE_FAILED, error.code)
    }

    @Test
    fun postProcessingFailureHasItsOwnCode() {
        val error = ErrorMapper.fromExit(Stage.POST_PROCESSING, 1, "ERROR: Conversion failed!")
        assertEquals(ErrorCode.POST_PROCESS_FAILED, error.code)
    }

    @Test
    fun botGateAndLoginAreDistinguishedFromUnavailable() {
        val gate = ErrorMapper.fromExit(
            Stage.RESOLVING,
            1,
            "ERROR: [youtube] xyz: Sign in to confirm you're not a bot. Use --cookies-from-browser or --cookies.",
        )
        assertEquals(ErrorCode.AGE_OR_BOT_GATE, gate.code)

        val unavailable = ErrorMapper.fromExit(Stage.RESOLVING, 1, "ERROR: [youtube] xyz: Video unavailable")
        assertEquals(ErrorCode.UNAVAILABLE, unavailable.code)
    }

    @Test
    fun geoBlockIsNotRetryable() {
        val error = ErrorMapper.fromExit(
            Stage.RESOLVING,
            1,
            "ERROR: [youtube] xyz: The uploader has not made this video available in your country",
        )
        assertEquals(ErrorCode.GEO_BLOCKED, error.code)
        assertEquals(false, error.retryable)
    }

    @Test
    fun withheldFormatsAreDetected() {
        val error = ErrorMapper.fromExit(
            Stage.DOWNLOADING,
            1,
            "ERROR: Requested format is not available. Use --list-formats for a list of available formats",
        )
        assertEquals(ErrorCode.FORMATS_WITHHELD, error.code)
    }

    @Test
    fun storageAndNetworkFailuresAreNamed() {
        assertEquals(
            ErrorCode.NO_SPACE,
            ErrorMapper.fromExit(Stage.DOWNLOADING, 1, "OSError: [Errno 28] No space left on device").code,
        )
        assertEquals(
            ErrorCode.NETWORK_OFFLINE,
            ErrorMapper.fromExit(
                Stage.DOWNLOADING,
                1,
                "ERROR: Unable to download webpage: <urlopen error [Errno -2] Name or service not known>",
            ).code,
        )
        assertEquals(
            ErrorCode.NETWORK_TIMEOUT,
            ErrorMapper.fromExit(Stage.DOWNLOADING, 1, "ERROR: Read timed out").code,
        )
        assertEquals(
            ErrorCode.RESOLVE_TIMEOUT,
            ErrorMapper.fromExit(Stage.RESOLVING, 1, "ERROR: Read timed out").code,
        )
    }

    @Test
    fun unknownResolveFailureFallsBackToEngineOutdated() {
        val error = ErrorMapper.fromExit(Stage.RESOLVING, 1, "ERROR: something new broke")
        assertEquals(ErrorCode.ENGINE_OUTDATED, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun stderrTailIsBoundedAndEmptyLinesDropped() {
        val output = (1..100).joinToString("\n") { if (it % 2 == 0) "" else "line $it" }
        val error = ErrorMapper.fromExit(Stage.DOWNLOADING, 9, output)
        assertEquals(40, error.stderrTail.size)
        assertEquals("line 99", error.stderrTail.last())
        assertTrue(error.stderrTail.none { it.isBlank() })
    }

    @Test
    fun logFieldsAreGreppable() {
        val fields = ErrorMapper.fromExit(Stage.DOWNLOADING, 1, "HTTP Error 403: Forbidden").logFields()
        assertTrue(fields.contains("code=HTTP_403_ON_MEDIA"))
        assertTrue(fields.contains("stage=DOWNLOADING"))
        assertTrue(fields.contains("exit=1"))
    }
}
