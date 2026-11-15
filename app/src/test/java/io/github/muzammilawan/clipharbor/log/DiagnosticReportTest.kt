package io.github.muzammilawan.clipharbor.log

import io.github.muzammilawan.clipharbor.core.EngineInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    private val context = ReportContext(
        appName = "ClipHarbor",
        versionName = "0.1.0-phase0",
        versionCode = "1",
        buildType = "debug",
        phase = "0",
        gitSha = "deadbee",
        deviceModel = "synthetic device",
        androidRelease = "17",
        apiLevel = "37",
        abi = "arm64-v8a",
        locale = "en-GB",
        freeSpaceMb = 12_345,
        engine = EngineInfo(
            libraryVersion = "0.18.1",
            ytdlpVersion = "2025.11.12",
            jsRuntime = "quickjs-2021-03-27",
            ffmpegVersion = "7.1",
        ),
        settings = mapOf("verbose" to "true", "updateChannel" to "stable"),
        permissions = mapOf("notifications" to "granted"),
    )

    @Test
    fun headerCarriesEveryFieldTheDebugPromptNeeds() {
        val header = DiagnosticReport.header(context)
        listOf(
            "== DIAGNOSTIC REPORT ==",
            "app=ClipHarbor versionName=0.1.0-phase0 versionCode=1",
            "buildType=debug",
            "phase=0",
            "android=17 api=37 abi=arm64-v8a",
            "freeSpaceMB=12345",
            "engine: library=0.18.1 ytdlp=2025.11.12",
            "jsRuntime=quickjs-2021-03-27",
            "ffmpeg=7.1",
            "settings: verbose=true updateChannel=stable",
            "permissions: notifications=granted",
        ).forEach { assertTrue("missing '$it' in:\n$header", header.contains(it)) }
    }

    @Test
    fun missingValuesAreLabelledNotInvented() {
        val sparse = context.copy(
            gitSha = null,
            freeSpaceMb = null,
            engine = EngineInfo(),
            settings = emptyMap(),
            permissions = emptyMap(),
        )
        val header = DiagnosticReport.header(sparse)
        assertTrue(header.contains("gitSha=unknown"))
        assertTrue(header.contains("freeSpaceMB=unknown"))
        assertTrue(header.contains("jsRuntime=missing"))
        assertTrue(header.contains("settings: none"))
    }

    @Test
    fun summaryIncludesHighlightsAndIsBounded() {
        val highlights = (1..80).map { "W task=$it stage=DOWNLOADING event=fail" }
        val summary = DiagnosticReport.summary(context, highlights)
        assertTrue(summary.startsWith("== DIAGNOSTIC REPORT =="))
        assertTrue(summary.contains("highlights:"))
        assertTrue(summary.contains("task=80"))
        assertTrue(!summary.contains("task=1 stage="))
    }
}
