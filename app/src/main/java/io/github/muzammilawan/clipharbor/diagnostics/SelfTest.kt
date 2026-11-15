package io.github.muzammilawan.clipharbor.diagnostics

import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.EngineInfo
import io.github.muzammilawan.clipharbor.core.FormatPlanner
import io.github.muzammilawan.clipharbor.log.RotatingLog

enum class CheckStatus { PASS, FAIL, SKIP }

data class CheckResult(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val detail: String,
) {
    /** Greppable log line: `check=js-runtime status=PASS detail="..."`. */
    fun logLine(): String =
        "check=$id status=${status.name} detail=\"" + detail.replace('"', '\'').take(300) + "\""
}

data class ProbeResult(val ok: Boolean, val detail: String)

/** Device checks that the JVM cannot do; faked in unit tests. */
interface StorageProbe {
    suspend fun appPrivateRoundTrip(): ProbeResult
    suspend fun mediaStoreInsert(): ProbeResult
    fun freeSpaceMb(): Long?
}

interface NetworkProbe {
    suspend fun dnsAndTls(host: String): ProbeResult
}

data class SelfTestReport(
    val checks: List<CheckResult>,
    val startedAtUtc: String,
) {
    val failures: Int get() = checks.count { it.status == CheckStatus.FAIL }
    val passes: Int get() = checks.count { it.status == CheckStatus.PASS }
    val skipped: Int get() = checks.count { it.status == CheckStatus.SKIP }

    fun summaryLine(): String =
        "SELFTEST RESULT: " + (if (failures == 0) "PASS" else "FAIL") + " ($passes/${checks.size} checks"

    fun reportText(): String = buildString {
        appendLine("== SELF-TEST $startedAtUtc ==")
        checks.forEach { appendLine(it.logLine()) }
        append(summaryLine())
        if (skipped > 0) append(", skipped=$skipped")
        append(")")
    }
}

/**
 * The Phase 0 self-test (docs/12 §3). Checks 1, 2, 3, 5, 6 are implemented; the foreground-service check needs the
 * Phase 1 service, and the download test is a separate user action on the Diagnostics screen.
 */
class SelfTestRunner(
    private val engine: Engine,
    private val storage: StorageProbe,
    private val network: NetworkProbe,
    private val host: String = "www.youtube.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val onLog: (String) -> Unit = {},
) {
    suspend fun run(url: String?): SelfTestReport {
        val started = RotatingLog.timestamp(clock())
        val checks = mutableListOf<CheckResult>()

        val init = runCatching { engine.init() }
        val info: EngineInfo = init.getOrNull()?.getOrNull() ?: engine.info()
        val initMessage = init.exceptionOrNull()?.message
            ?: init.getOrNull()?.exceptionOrNull()?.message

        checks += if (init.isSuccess && init.getOrNull()?.isSuccess == true) {
            CheckResult("engine-init", "Engine unpack", CheckStatus.PASS, info.headerLine())
        } else {
            CheckResult("engine-init", "Engine unpack", CheckStatus.FAIL, initMessage ?: "engine init failed")
        }
        checks += engineTool(
            id = "js-runtime",
            title = "JavaScript runtime",
            present = info.hasJsRuntime(),
            value = info.jsRuntime ?: "missing",
        )
        checks += engineTool(
            id = "ffmpeg",
            title = "ffmpeg present",
            present = info.hasFfmpeg(),
            value = info.ffmpegVersion ?: "missing",
        )
        checks += probe("storage-private", "App-private storage", runCatching { storage.appPrivateRoundTrip() })
        checks += probe("storage-mediastore", "MediaStore insert", runCatching { storage.mediaStoreInsert() })
        checks += probe("network", "Network ($host)", runCatching { network.dnsAndTls(host) })

        checks += resolveCheck(url)

        checks.forEach { onLog(it.logLine()) }
        val report = SelfTestReport(checks, started)
        onLog(report.summaryLine())
        return report
    }

    private suspend fun resolveCheck(url: String?): CheckResult {
        if (url.isNullOrBlank()) {
            return CheckResult("resolve", "Resolve URL", CheckStatus.SKIP, "no URL entered")
        }
        val result = runCatching { engine.resolve(url.trim()) }
        return result.fold(
            onSuccess = { resolved ->
                resolved.fold(
                    onSuccess = { outcome ->
                        val summary = FormatPlanner.summary(outcome.media.formats)
                        val detail = FormatPlanner.describe(summary) +
                            " withheld=" + (summary.hdUnavailable || outcome.media.warnings.isNotEmpty()) +
                            " warnings=" + outcome.media.warnings.size
                        CheckResult("resolve", "Resolve URL", CheckStatus.PASS, detail)
                    },
                    onFailure = { error ->
                        CheckResult("resolve", "Resolve URL", CheckStatus.FAIL, error.message ?: "resolve failed")
                    },
                )
            },
            onFailure = { error ->
                CheckResult("resolve", "Resolve URL", CheckStatus.FAIL, error.message ?: "resolve crashed")
            },
        )
    }

    private fun engineTool(id: String, title: String, present: Boolean, value: String): CheckResult =
        CheckResult(
            id = id,
            title = title,
            status = if (present) CheckStatus.PASS else CheckStatus.FAIL,
            detail = if (present) value else "$value (bundled .so not found)",
        )

    private suspend fun probe(id: String, title: String, block: Result<ProbeResult>): CheckResult {
        val result = block.getOrElse { ProbeResult(false, it.message ?: "probe crashed") }
        return CheckResult(
            id = id,
            title = title,
            status = if (result.ok) CheckStatus.PASS else CheckStatus.FAIL,
            detail = result.detail,
        )
    }
}
