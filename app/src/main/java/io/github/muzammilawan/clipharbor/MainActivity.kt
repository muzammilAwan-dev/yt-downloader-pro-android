package io.github.muzammilawan.clipharbor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.SharedSessionOwner
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsScreen
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.feature.home.HomeScreen
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.feature.home.YtdlpTheme
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import kotlinx.coroutines.launch

/**
 * Single activity: Phase 1 link review/quality picker with separately accessible local diagnostics.
 * Attaches to a shared process-local session; no service/background protection is connected yet.
 */
class MainActivity : ComponentActivity() {
    private lateinit var attachment: SharedSessionOwner.Attachment<DownloadSession>
    private val session get() = attachment.value
    private val engine get() = session.engine
    private val logger get() = session.logger
    private val storage get() = session.storage
    private val home get() = session.home
    private val diagnostics get() = session.diagnostics
    private var showDiagnostics by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachment = (application as YtdlpApplication).sessions.attach()
        // Do not replay the original share over a retained task; fresh process/session still prefills it.
        if (savedInstanceState == null || home.state.value.url.isEmpty()) receiveShare(intent)
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
                }, onResolve = { if (!state.busy) home.resolve() }, onSelectQuality = home::selectQuality,
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
                    val current = session
                    attachment.scope.launch {
                        current.runSelfTest("selftest-" + java.util.UUID.randomUUID())
                        current.diagnostics.verbose = current.logger.verbose
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
        if (::attachment.isInitialized) attachment.close()
        super.onDestroy()
    }
}
