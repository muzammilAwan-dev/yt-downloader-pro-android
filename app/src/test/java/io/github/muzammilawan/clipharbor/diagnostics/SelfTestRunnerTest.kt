package io.github.muzammilawan.clipharbor.diagnostics

import io.github.muzammilawan.clipharbor.core.EngineInfo
import io.github.muzammilawan.clipharbor.core.MediaInfo
import io.github.muzammilawan.clipharbor.core.ResolveOutcome
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestRunnerTest {
    private class FakeStorage(var ok: Boolean = true, var free: Long = 4096) : StorageProbe {
        override suspend fun appPrivateRoundTrip(): ProbeResult =
            ProbeResult(ok, if (ok) "wrote/read/deleted 24 bytes" else "write failed")

        override suspend fun mediaStoreInsert(): ProbeResult =
            ProbeResult(ok, if (ok) "uri=content://media/external/downloads/1" else "insert failed")

        override fun freeSpaceMb(): Long = free
    }

    private class FakeNetwork(var ok: Boolean = true) : NetworkProbe {
        override suspend fun dnsAndTls(host: String): ProbeResult =
            ProbeResult(ok, if (ok) "dns+tls ok host=$host" else "handshake failed")
    }

    @Test
    fun allChecksPassWithoutAUrlAndTheResolveIsSkipped() = runTest {
        val runner = SelfTestRunner(FakeEngine(), FakeStorage(), FakeNetwork(), clock = { 0L })
        val report = runner.run(url = null)

        assertEquals(0, report.failures)
        assertEquals(1, report.skipped)
        assertTrue(report.checks.any { it.id == "resolve" && it.status == CheckStatus.SKIP })
        assertTrue(report.reportText().contains("SELFTEST RESULT: PASS (6/7 checks, skipped=1)"))
    }

    @Test
    fun failedEngineInitFailsTheReportAndSkipsDependentEvidence() = runTest {
        val engine = FakeEngine(initResult = Result.failure(IllegalStateException("unzip failed")))
        val report = SelfTestRunner(engine, FakeStorage(), FakeNetwork(), clock = { 0L }).run(null)

        assertEquals(CheckStatus.FAIL, report.checks.first { it.id == "engine-init" }.status)
        assertTrue(report.reportText().startsWith("== SELF-TEST"))
        assertTrue(report.summaryLine().startsWith("SELFTEST RESULT: FAIL"))
    }

    @Test
    fun resolveCheckReportsFormatSummary() = runTest {
        val report = SelfTestRunner(FakeEngine(), FakeStorage(), FakeNetwork(), clock = { 0L })
            .run("https://example.invalid/watch?v=dQw4w9WgXcQ")

        val resolve = report.checks.first { it.id == "resolve" }
        assertEquals(CheckStatus.PASS, resolve.status)
        assertTrue(resolve.detail.contains("formats=4"))
        assertTrue(resolve.detail.contains("maxHeight=1080"))
        assertTrue(resolve.detail.contains("withheld=false"))
    }

    @Test
    fun warningsMarkFormatsAsWithheld() = runTest {
        val engine = FakeEngine(
            resolveResult = Result.success(
                ResolveOutcome(
                    media = MediaInfo(
                        id = "x",
                        title = "t",
                        formats = FakeEngine.SAMPLE_FORMATS,
                        warnings = listOf("WARNING: [youtube] Some formats may be missing"),
                    ),
                    engineInfo = EngineInfo(libraryVersion = "0.18.1"),
                    debugTail = emptyList(),
                ),
            ),
        )
        val resolve = SelfTestRunner(engine, FakeStorage(), FakeNetwork(), clock = { 0L })
            .run("https://example.invalid/watch?v=x")
            .checks.first { it.id == "resolve" }

        assertEquals(CheckStatus.PASS, resolve.status)
        assertTrue(resolve.detail.contains("withheld=true"))
        assertTrue(resolve.detail.contains("warnings=1"))
    }

    @Test
    fun probeFailureIsReportedWithoutThrowing() = runTest {
        val report = SelfTestRunner(FakeEngine(), FakeStorage(ok = false), FakeNetwork(ok = false), clock = { 0L })
            .run(null)

        assertEquals(3, report.failures)
        assertEquals(
            CheckStatus.FAIL,
            report.checks.first { it.id == "storage-private" }.status,
        )
        assertFalse(report.reportText().isBlank())
    }
}
