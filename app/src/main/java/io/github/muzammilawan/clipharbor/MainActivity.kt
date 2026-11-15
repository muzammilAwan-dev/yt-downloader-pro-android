package io.github.muzammilawan.clipharbor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.diagnostics.AndroidNetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.AndroidStorageProbe
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsScreen
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.NetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.diagnostics.SelfTestRunner
import io.github.muzammilawan.clipharbor.diagnostics.StorageProbe
import io.github.muzammilawan.clipharbor.engine.YoutubeDlEngine
import io.github.muzammilawan.clipharbor.feature.home.HomeController
import io.github.muzammilawan.clipharbor.feature.home.HomeObserver
import io.github.muzammilawan.clipharbor.feature.home.HomeScreen
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.feature.home.YtdlpTheme
import io.github.muzammilawan.clipharbor.log.AppLogger
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.RotatingLog
import io.github.muzammilawan.clipharbor.publish.MediaStorePublisher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single activity: Phase 1 link review/quality picker with separately accessible local diagnostics.
 * Activity-lifetime execution only until the durable queue/service increment.
 */
class MainActivity : ComponentActivity() {
    private lateinit var engine: Engine
    private lateinit var logger: AppLogger
    private lateinit var storage: StorageProbe
    private lateinit var network: NetworkProbe
    private lateinit var publisher: MediaStorePublisher

    private lateinit var home: HomeController
    private val diagnostics = DiagnosticsState()
    private var showDiagnostics by mutableStateOf(false)

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

        home = HomeController(engine, lifecycleScope, File(filesDir, "staging"), publish = { file ->
            val mime = mimeTypeFor(file)
            val uri = withContext(Dispatchers.IO) { publisher.publish(file, mime).getOrThrow() }
            PublishedMedia(uri.toString(), file.name,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads/${AppInfo.DISPLAY_NAME}"
                else "App-specific downloads (removed if the app is uninstalled)", mime)
        }, observer = object : HomeObserver {
            override fun event(task: String?, stage: String, event: String, fields: Map<String, String>) {
                logger.event('I', event, task = task, stage = stage, fields = fields,
                    message = if (event == "engine_init") engine.info().headerLine() else null)
            }
            override fun failure(task: String?, error: DownloadError) = logFailure(task, error)
            override fun line(task: String, line: String) {
                logger.write(RotatingLog.timestamp(System.currentTimeMillis()) +
                    " V task=$task stage=DOWNLOADING event=ytdlp " + line)
            }
        })
        receiveShare(intent)
        home.initialize()

        setContent {
            YtdlpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoute()
                }
            }
        }
    }

    @Composable
    private fun AppRoute() {
        val ui by home.state.collectAsState()
        val state = diagnostics
        BackHandler(enabled = showDiagnostics) { showDiagnostics = false }
        if (!showDiagnostics) {
            HomeScreen(state = ui, externalBusy = state.busy, onUrlChange = home::editUrl,
                onPaste = {
                    val text = getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                    if (text != null) home.receiveShared(text.take(MediaEntry.MAX_INPUT_CHARS + 1).toString())
                    else home.receiveShared("")
                }, onResolve = { if (!state.busy) home.resolve() }, onSelectHeight = home::selectHeight,
                onDownload = { if (!state.busy) home.download() }, onCancel = home::cancel,
                onRetryEngine = { if (!state.busy) home.initialize() },
                onPendingLink = home::usePendingLink, onOpenFile = ::openSaved,
                onDiagnostics = {
                    state.engineLine = "engine: " + engine.info().headerLine()
                    state.logTail = logger.snapshot()
                    state.verbose = logger.verbose
                    showDiagnostics = true
                })
            return
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            OutlinedButton(onClick = { showDiagnostics = false }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Back to downloads")
            }
            DiagnosticsScreen(
                state = state,
                externalBusy = ui.busy,
                onUrlChange = { state.url = it },
                onRunSelfTest = {
                    lifecycleScope.launch {
                        runSelfTest(state, "selftest-" + java.util.UUID.randomUUID())
                        state.verbose = logger.verbose
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        showDiagnostics = false
        val text = if (intent.type == "text/plain") runCatching {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }.getOrNull() else null
        // Never read binary streams, grant URIs, automation extras or custom engine options.
        home.receiveShared(text?.take(MediaEntry.MAX_INPUT_CHARS + 1)?.toString().orEmpty())
    }

    private fun openSaved(saved: PublishedMedia) {
        val uri = saved.uri.toUri()
        if (uri.scheme != "content") return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, saved.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure {
            diagnostics.status = "No compatible viewer opened. Find the file in ${saved.location}."
            showDiagnostics = true
        }
    }

    private suspend fun runSelfTest(state: DiagnosticsState, taskId: String) {
        if (state.busy || home.state.value.busy) return
        state.busy = true
        state.status = "Running self-test…"
        logger.event('I', "start", task = taskId, stage = "SELFTEST")
        val started = System.currentTimeMillis()
        try {
            val runner = SelfTestRunner(engine, storage, network, onLog = { logger.write(it) })
            val validated = if (state.url.isBlank()) null else MediaEntry.pasted(state.url)
            if (validated is MediaEntry.Input.Invalid) {
                state.status = validated.message
                return
            }
            val report = runner.run((validated as? MediaEntry.Input.Link)?.url)
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

    private fun logFailure(taskId: String?, error: DownloadError) {
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
                putExtra(Intent.EXTRA_SUBJECT, "${AppInfo.DISPLAY_NAME} diagnostic log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${AppInfo.DISPLAY_NAME} log"))
        }.onFailure { state.status = "Could not share log: ${it.message}" }
    }

    private fun copySummary(state: DiagnosticsState) {
        val context = ReportContextFactory.build(this, engine.info(), logger.verbose, storage.freeSpaceMb())
        val highlights = logger.snapshot(400).lines().filter { it.contains(" E ") || it.contains(" W ") }.takeLast(40)
        val summary = DiagnosticReport.summary(context, highlights) + "\n\n" + state.report
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("${AppInfo.DISPLAY_NAME} diagnostic summary", summary))
        state.status = "Summary copied to the clipboard."
    }

    override fun onDestroy() {
        if (::home.isInitialized) home.close()
        logger.event('I', "app_stop", stage = "APP")
        logger.close()
        super.onDestroy()
    }
}
