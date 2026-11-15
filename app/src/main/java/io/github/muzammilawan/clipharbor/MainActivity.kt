package io.github.muzammilawan.clipharbor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.FormatPlanner
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.diagnostics.AndroidNetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.AndroidStorageProbe
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsScreen
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.NetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.diagnostics.SelfTestRunner
import io.github.muzammilawan.clipharbor.diagnostics.StorageProbe
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.engine.YoutubeDlEngine
import io.github.muzammilawan.clipharbor.log.AppLogger
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.RotatingLog
import io.github.muzammilawan.clipharbor.publish.MediaStorePublisher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single activity (docs/02 §1). Phase 0 renders the diagnostics spike: self-test, a 1080p+audio download with merge,
 * MediaStore publish, and the log actions required from Phase 0 onward.
 */
class MainActivity : ComponentActivity() {
    private lateinit var engine: Engine
    private lateinit var logger: AppLogger
    private lateinit var storage: StorageProbe
    private lateinit var network: NetworkProbe
    private lateinit var publisher: MediaStorePublisher

    @Volatile
    private var runningTaskId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = YoutubeDlEngine(applicationContext)
        logger = AppLogger(this)
        storage = AndroidStorageProbe(applicationContext)
        network = AndroidNetworkProbe()
        publisher = MediaStorePublisher(applicationContext)

        val header = DiagnosticReport.header(
            ReportContextFactory.build(this, engine.info(), logger.verbose, storage.freeSpaceMb()),
        )
        logger.startSession(header)
        logger.event(
            level = 'I',
            event = "app_start",
            stage = "APP",
            fields = mapOf("build" to BuildConfig.BUILD_TYPE, "version" to BuildConfig.VERSION_NAME),
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiagnosticsRoute()
                }
            }
        }
    }

    @Composable
    private fun DiagnosticsRoute() {
        val state = remember { DiagnosticsState() }
        val scope = rememberCoroutineScope()
        val taskId = remember { "t" + (System.currentTimeMillis() % 1_000_000) }
        var engineReady by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            state.busy = true
            state.status = "Unpacking engine (first run takes a few seconds)…"
            val started = System.currentTimeMillis()
            val result = engine.init()
            val info = result.getOrNull()
            if (info != null) {
                state.engineLine = "engine: " + info.headerLine()
                logger.event(
                    level = 'I',
                    event = "engine_init",
                    stage = "APP",
                    fields = mapOf("ms" to (System.currentTimeMillis() - started).toString()),
                    message = info.headerLine(),
                )
                state.status = "Engine ready."
            } else {
                state.engineLine = "engine: init failed — see log"
                logger.event(
                    level = 'E',
                    event = "engine_init_failed",
                    stage = "APP",
                    message = result.exceptionOrNull()?.message,
                )
                state.status = "Engine init failed. Tap Run self-test and share the log."
            }
            engineReady = info != null
            state.verbose = logger.verbose
            state.logTail = logger.snapshot()
            state.busy = false
        }

        DiagnosticsScreen(
            state = state,
            onUrlChange = { state.url = it },
            onRunSelfTest = {
                scope.launch {
                    runSelfTest(state, taskId)
                    state.verbose = logger.verbose
                }
            },
            onDownload = {
                scope.launch {
                    if (engineReady) download1080p(state, taskId) else state.status = "Engine is not ready yet."
                }
            },
            onShareLog = { shareLog(state) },
            onCopySummary = { copySummary(state) },
            onClearLogs = {
                logger.clear()
                state.logTail = ""
                state.status = "Logs cleared."
            },
            onVerboseChange = {
                logger.verbose = it
                state.verbose = it
                logger.event('I', "verbose_changed", stage = "APP", fields = mapOf("verbose" to it.toString()))
                state.logTail = logger.snapshot()
            },
        )
    }

    private suspend fun runSelfTest(state: DiagnosticsState, taskId: String) {
        state.busy = true
        state.status = "Running self-test…"
        logger.event('I', "start", task = taskId, stage = "SELFTEST")
        val started = System.currentTimeMillis()
        try {
            val runner = SelfTestRunner(engine, storage, network, onLog = { logger.write(it) })
            val report = runner.run(state.url.trim().ifBlank { null })
            logger.event(
                level = 'I',
                event = "done",
                task = taskId,
                stage = "SELFTEST",
                fields = mapOf(
                    "failures" to report.failures.toString(),
                    "ms" to (System.currentTimeMillis() - started).toString(),
                ),
            )
            state.report = report.reportText()
            state.status = report.summaryLine()
            state.engineLine = "engine: " + engine.info().headerLine()
        } finally {
            state.busy = false
            state.logTail = logger.snapshot()
        }
    }

    private suspend fun download1080p(state: DiagnosticsState, taskId: String) {
        val url = state.url.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            state.status = "Paste a full HTTPS link first."
            return
        }
        state.busy = true
        state.report = ""
        runningTaskId = taskId
        state.status = "Resolving…"
        logger.event(
            level = 'I',
            event = "start",
            task = taskId,
            stage = Stage.RESOLVING.name,
            // AppLogger redacts the complete line exactly once before either copy reaches disk.
            fields = mapOf("url" to url),
        )
        val resolveStarted = System.currentTimeMillis()
        var snapshot: ResolvedSnapshot? = null
        try {
            val resolved = engine.resolveForTask(url, taskId)
            if (resolved.isFailure) {
                fail(state, taskId, resolved.exceptionOrNull())
                return
            }
            val outcome = resolved.getOrThrow()
            snapshot = outcome.resolvedSnapshot
            val summary = FormatPlanner.summary(outcome.media.formats)
            val withheld = summary.hdUnavailable || outcome.media.warnings.isNotEmpty()
            logger.event(
                level = if (withheld) 'W' else 'I',
                event = "ok",
                task = taskId,
                stage = Stage.RESOLVING.name,
                fields = mapOf(
                    "formats" to summary.total.toString(),
                    "maxHeight" to (summary.maxHeight?.toString() ?: "none"),
                    "withheld" to withheld.toString(),
                    "ms" to (System.currentTimeMillis() - resolveStarted).toString(),
                ),
                message = outcome.media.title,
            )
            state.status = "Resolved: ${outcome.media.title} (max ${summary.maxHeight ?: "?"}p)" +
                if (withheld) " — HD may be withheld" else ""

            val workingDir = File(filesDir, "staging/$taskId")
            var lastPercent = -1
            val downloadStarted = System.currentTimeMillis()
            logger.event('I', "start", task = taskId, stage = Stage.DOWNLOADING.name,
                fields = mapOf("resolveReuse" to (snapshot != null).toString(), "concurrentFragments" to "4"))
            val downloaded = engine.download(
                request = DownloadRequest(
                    url = url,
                    maxHeight = 1080,
                    outputDirectory = workingDir,
                    processId = taskId,
                    resolvedSnapshot = snapshot,
                ),
                onProgress = { event ->
                    val percent = event.percent?.toInt() ?: -1
                    if (percent >= 0 && percent != lastPercent) {
                        lastPercent = percent
                        runOnUiThread { state.status = "Downloading… $percent%" }
                    }
                },
                onLine = { line ->
                    if (line.isNotBlank()) {
                        logger.write(
                            RotatingLog.timestamp(System.currentTimeMillis()) +
                                " V task=$taskId stage=DOWNLOADING event=ytdlp " +
                                line,
                        )
                    }
                },
            )
            if (downloaded.isFailure) {
                fail(state, taskId, downloaded.exceptionOrNull())
                return
            }
            val downloadOutcome = downloaded.getOrThrow()
            logger.event(
                level = if (downloadOutcome.selectedHeight?.let { it < 1080 } == true) 'W' else 'I',
                event = "selected_formats", task = taskId, stage = Stage.DOWNLOADING.name,
                fields = mapOf(
                    "ids" to downloadOutcome.selectedFormatIds.joinToString("+").ifBlank { "unknown" },
                    "actualHeight" to (downloadOutcome.selectedHeight?.toString() ?: "unknown"),
                    "requestedHeight" to "1080", "reusedResolve" to downloadOutcome.reusedResolve.toString(),
                ),
            )
            val file = downloadOutcome.files.first()
            logger.event(
                level = 'I',
                event = "ok",
                task = taskId,
                stage = Stage.MERGING.name,
                fields = mapOf(
                    "file" to file.name,
                    "bytes" to file.length().toString(),
                    "ms" to (System.currentTimeMillis() - downloadStarted).toString(),
                ),
            )

            state.status = "Publishing to Downloads/ClipHarbor…"
            logger.event('I', "start", task = taskId, stage = Stage.PUBLISHING.name)
            val published = withContext(Dispatchers.IO) {
                publisher.publish(file, mimeTypeFor(file))
            }
            if (published.isFailure) {
                fail(state, taskId, published.exceptionOrNull())
                return
            }
            val uri = published.getOrThrow()
            logger.event(
                level = 'I',
                event = "ok",
                task = taskId,
                stage = Stage.PUBLISHING.name,
                fields = mapOf("uri" to uri.toString(), "mime" to mimeTypeFor(file)),
            )
            state.status = "Saved ${file.name} to Downloads/ClipHarbor ($uri)"
            workingDir.deleteRecursively()
        } finally {
            snapshot?.close()
            runningTaskId = null
            state.busy = false
            state.logTail = logger.snapshot()
        }
    }

    private fun fail(state: DiagnosticsState, taskId: String, throwable: Throwable?) {
        val error = (throwable as? EngineFailure)?.error
            ?: DownloadError(ErrorCode.UNAVAILABLE, Stage.ANY, detail = throwable?.message)
        logger.event(
            level = 'E',
            event = "fail",
            task = taskId,
            stage = error.stage.name,
            fields = buildMap {
                put("code", error.code.name)
                put("retryable", error.retryable.toString())
                error.exitCode?.let { put("exit", it.toString()) }
            },
            message = error.detail,
        )
        // Last lines of yt-dlp output for a failed task (docs/12 §4); already redacted by the log writer.
        error.stderrTail.takeLast(200).forEach { line ->
            logger.write(
                RotatingLog.timestamp(System.currentTimeMillis()) +
                    " E task=$taskId stage=${error.stage} event=ytdlp " + line,
            )
        }
        state.status = "Failed: ${error.code.name}. See the log (Share log) for details."
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> "video/mp4"
    }

    private fun shareLog(state: DiagnosticsState) {
        val file = logger.newestLogFile()
        if (file == null) {
            state.status = "No log file yet."
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ClipHarbor diagnostic log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ClipHarbor log"))
        }.onFailure { state.status = "Could not share log: ${it.message}" }
    }

    private fun copySummary(state: DiagnosticsState) {
        val context = ReportContextFactory.build(this, engine.info(), logger.verbose, storage.freeSpaceMb())
        val highlights = logger.snapshot(400).lines().filter { it.contains(" E ") || it.contains(" W ") }.takeLast(40)
        val summary = DiagnosticReport.summary(context, highlights) + "\n\n" + state.report
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("ClipHarbor diagnostic summary", summary))
        state.status = "Summary copied to the clipboard."
    }

    override fun onDestroy() {
        runningTaskId?.let { engine.cancel(it) }
        logger.event('I', "app_stop", stage = "APP")
        logger.close()
        super.onDestroy()
    }
}
