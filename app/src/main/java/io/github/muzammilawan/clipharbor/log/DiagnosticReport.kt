package io.github.muzammilawan.clipharbor.log

import io.github.muzammilawan.clipharbor.core.EngineInfo

/**
 * Builds the header that makes a shared log self-explanatory (docs/12 §4). Platform-free: the caller supplies the
 * device values, so the format is unit-tested on the JVM.
 */
data class ReportContext(
    val appName: String,
    val versionName: String,
    val versionCode: String,
    val buildType: String,
    val phase: String,
    val gitSha: String?,
    val deviceModel: String,
    val androidRelease: String,
    val apiLevel: String,
    val abi: String,
    val locale: String,
    val freeSpaceMb: Long?,
    val engine: EngineInfo,
    val settings: Map<String, String> = emptyMap(),
    val permissions: Map<String, String> = emptyMap(),
)

object DiagnosticReport {
    fun header(context: ReportContext): String = buildString {
        appendLine("== DIAGNOSTIC REPORT ==")
        append("app=").append(context.appName)
        append(" versionName=").append(context.versionName)
        append(" versionCode=").append(context.versionCode)
        append(" gitSha=").append(context.gitSha ?: "unknown")
        append(" buildType=").append(context.buildType)
        appendLine(" phase=" + context.phase)
        append("device=").append(context.deviceModel)
        append(" android=").append(context.androidRelease)
        append(" api=").append(context.apiLevel)
        append(" abi=").append(context.abi)
        append(" locale=").append(context.locale)
        appendLine(" freeSpaceMB=" + (context.freeSpaceMb?.toString() ?: "unknown"))
        appendLine("engine: " + context.engine.headerLine())
        appendLine("settings: " + formatPairs(context.settings))
        append("permissions: ").append(formatPairs(context.permissions))
    }

    /** Header + a compact list of the last warnings/errors, for the "Copy summary" button. */
    fun summary(context: ReportContext, highlights: List<String>): String = buildString {
        appendLine(header(context))
        if (highlights.isEmpty()) {
            append("highlights: none")
        } else {
            appendLine("highlights:")
            highlights.takeLast(MAX_HIGHLIGHTS).forEach { append("  ").append(it) }
        }
    }

    private fun formatPairs(map: Map<String, String>): String =
        if (map.isEmpty()) "none" else map.entries.joinToString(" ") { "${it.key}=${it.value}" }

    private const val MAX_HIGHLIGHTS = 40
}
