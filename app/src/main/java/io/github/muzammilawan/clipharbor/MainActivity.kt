package io.github.muzammilawan.clipharbor

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.SharedSessionOwner
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsScreen
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.download.DownloadService
import io.github.muzammilawan.clipharbor.download.NotificationAccess
import io.github.muzammilawan.clipharbor.download.notificationAccess
import io.github.muzammilawan.clipharbor.feature.home.HomePhase
import io.github.muzammilawan.clipharbor.feature.home.HomeScreen
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.feature.home.YtdlpTheme
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.UserAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single activity: Phase 1 link review/quality picker with separately accessible local diagnostics.
 * Attaches to the shared task/service session; no process-death recovery or durable queue.
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
    private var notificationVisibility by mutableStateOf(NotificationAccess.DISABLED)
    private var notificationRequestPending by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationRequestPending = false
        refreshNotificationAccess() // Re-read platform state, never resume/start a download from this callback.
        if (::attachment.isInitialized) logger.event('I', "notification_permission_result", stage = "APP",
            fields = mapOf("access" to notificationVisibility.name))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachment = (application as YtdlpApplication).sessions.attach()
        logger.action(UserAction.ACTIVITY_CREATE, home.state.value.phase.name)
        // Do not replay the original share over a retained task; fresh process/session still prefills it.
        if (savedInstanceState == null || home.state.value.url.isEmpty()) receiveShare(intent)
        home.initialize()
        refreshNotificationAccess()

        setContent {
            YtdlpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoute()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationAccess()
        if (::attachment.isInitialized) logger.action(UserAction.ACTIVITY_RESUME, home.state.value.phase.name)
    }

    override fun onPause() {
        if (::attachment.isInitialized) logger.action(UserAction.ACTIVITY_PAUSE, home.state.value.phase.name)
        super.onPause()
    }

    override fun onStop() {
        if (::attachment.isInitialized) logger.action(UserAction.ACTIVITY_STOP, home.state.value.phase.name)
        super.onStop()
    }

    private fun refreshNotificationAccess() {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            getSystemService(NotificationManager::class.java).getNotificationChannel(DownloadService.CHANNEL)?.importance !=
            NotificationManager.IMPORTANCE_NONE
        notificationVisibility = notificationAccess(Build.VERSION.SDK_INT, permissionGranted,
            NotificationManagerCompat.from(this).areNotificationsEnabled(), channelEnabled)
    }

    private fun requestNotifications() {
        logger.action(UserAction.ENABLE_NOTIFICATIONS, home.state.value.phase.name)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationRequestPending ||
            home.state.value.phase != HomePhase.CHOOSING || diagnostics.busy) return
        refreshNotificationAccess()
        if (notificationVisibility != NotificationAccess.PERMISSION_REQUIRED) return
        notificationRequestPending = true
        try { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        catch (_: Exception) {
            notificationRequestPending = false
            logger.event('W', "notification_permission_unavailable", stage = "APP")
        }
    }

    @Composable
    private fun AppRoute() {
        val ui by home.state.collectAsState()
        val state = diagnostics
        BackHandler(enabled = showDiagnostics) {
            logger.action(UserAction.BACK_TO_DOWNLOADS, ui.phase.name)
            showDiagnostics = false
        }
        if (!showDiagnostics) {
            HomeScreen(state = ui, externalBusy = state.busy, onUrlChange = {
                logger.action(UserAction.EDIT_LINK, ui.phase.name)
                home.editUrl(it)
            },
                notificationAccess = notificationVisibility, notificationRequestPending = notificationRequestPending,
                onRequestNotifications = ::requestNotifications,
                onPaste = {
                    logger.action(UserAction.PASTE_LINK, ui.phase.name)
                    val text = getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                    if (text != null) home.receiveShared(text.take(MediaEntry.MAX_INPUT_CHARS + 1).toString())
                    else home.receiveShared("")
                }, onResolve = {
                    logger.action(UserAction.FIND_FORMATS, ui.phase.name)
                    if (!state.busy) home.resolve(forceRefresh = home.state.value.phase == io.github.muzammilawan.clipharbor.feature.home.HomePhase.CHOOSING)
                }, onSelectQuality = {
                    logger.action(UserAction.SELECT_QUALITY, ui.phase.name, qualityPixels = it.pixels)
                    home.selectQuality(it)
                }, onSelectAudio = {
                    logger.action(UserAction.SELECT_AUDIO, ui.phase.name)
                    home.selectAudio(it)
                }, onDownload = {
                    logger.action(UserAction.DOWNLOAD, ui.phase.name, qualityPixels = ui.selectedQuality?.pixels)
                    if (!state.busy) home.download()
                }, onCancel = {
                    logger.action(UserAction.CANCEL, ui.phase.name)
                    home.cancel()
                }, onRetryEngine = {
                    logger.action(UserAction.RETRY_ENGINE, ui.phase.name)
                    if (!state.busy) home.initialize()
                }, onPendingLink = {
                    logger.action(UserAction.USE_PENDING_LINK, ui.phase.name)
                    home.usePendingLink()
                }, onOpenFile = ::openSaved,
                onDiagnostics = {
                    logger.action(UserAction.OPEN_DIAGNOSTICS, ui.phase.name)
                    state.engineLine = "engine: " + engine.info().headerLine()
                    state.logTail = logger.snapshot()
                    state.verbose = logger.verbose
                    showDiagnostics = true
                })
            return
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            OutlinedButton(onClick = {
                logger.action(UserAction.BACK_TO_DOWNLOADS, ui.phase.name)
                showDiagnostics = false
            }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Back to downloads")
            }
            DiagnosticsScreen(
                state = state,
                externalBusy = ui.busy,
                onUrlChange = {
                    logger.action(UserAction.EDIT_SELFTEST_LINK, ui.phase.name)
                    state.url = it
                },
                onRunSelfTest = {
                    logger.action(UserAction.RUN_SELF_TEST, ui.phase.name)
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
                    state.logTail = logger.snapshot()
                    state.status = "Logs cleared. A new timeline starts here."
                },
                onVerboseChange = {
                    logger.verbose = it
                    state.verbose = it
                    logger.action(UserAction.SET_VERBOSE, ui.phase.name, enabled = it)
                    state.logTail = logger.snapshot()
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logger.action(UserAction.NEW_INTENT, home.state.value.phase.name)
        setIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        logger.action(UserAction.SHARE_LINK_RECEIVED, home.state.value.phase.name)
        showDiagnostics = false
        val text = if (intent.type == "text/plain") runCatching {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }.getOrNull() else null
        // Never read binary streams, grant URIs, automation extras or custom engine options.
        home.receiveShared(text?.take(MediaEntry.MAX_INPUT_CHARS + 1)?.toString().orEmpty())
    }

    private fun openSaved(saved: PublishedMedia) {
        logger.action(UserAction.OPEN_SAVED_FILE, home.state.value.phase.name)
        val uri = saved.uri.toUri()
        if (uri.scheme != "content") return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, saved.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            logger.event('I', "open_file_launched", stage = "UI")
        }.onFailure {
            logger.event('W', "open_file_failed", stage = "UI", fields = mapOf("error" to it.javaClass.simpleName))
            diagnostics.status = "No compatible viewer opened. Find the file in ${saved.location}."
            showDiagnostics = true
        }
    }

    private fun shareLog(state: DiagnosticsState) {
        if (state.sharingLog) return
        val currentLogger = logger
        val cutoff = try { currentLogger.captureShare(home.state.value.phase.name) } catch (_: Exception) {
            state.status = "Full session log is unavailable. Clear logs to start a fresh debugging timeline."
            return
        }
        state.sharingLog = true
        state.status = "Preparing the full session log…"
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { currentLogger.exportSession(cutoff) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${AppInfo.DISPLAY_NAME} session diagnostic log")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share ${AppInfo.DISPLAY_NAME} session log"))
                currentLogger.event('I', "share_chooser_opened", stage = "UI", fields = mapOf("bytes" to file.length().toString()))
                state.status = "Session snapshot ready: startup (or Clear logs) through the Share tap. Choose an app to share it."
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                currentLogger.event('W', "share_log_failed", stage = "UI", fields = mapOf("error" to error.javaClass.simpleName))
                state.status = "Could not prepare or share the full session log. No partial log was shared."
            } finally {
                state.sharingLog = false
            }
        }
    }

    private fun copySummary(state: DiagnosticsState) {
        logger.action(UserAction.COPY_SUMMARY, home.state.value.phase.name)
        val context = ReportContextFactory.build(this, engine.info(), logger.verbose, storage.freeSpaceMb())
        val highlights = logger.snapshot(400).lines().filter { it.contains(" E ") || it.contains(" W ") }.takeLast(40)
        val summary = DiagnosticReport.summary(context, highlights) + "\n\n" + state.report
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("${AppInfo.DISPLAY_NAME} diagnostic summary", summary))
        state.status = "Summary copied to the clipboard."
    }

    override fun onDestroy() {
        if (::attachment.isInitialized) {
            logger.action(UserAction.ACTIVITY_DESTROY, home.state.value.phase.name)
            attachment.close()
        }
        super.onDestroy()
    }
}
