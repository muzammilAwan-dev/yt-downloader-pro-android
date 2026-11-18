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
import io.github.muzammilawan.clipharbor.core.SiteShortcut
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsScreen
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.download.DownloadService
import io.github.muzammilawan.clipharbor.download.NotificationAccess
import io.github.muzammilawan.clipharbor.download.TransferPreferences
import io.github.muzammilawan.clipharbor.download.notificationAccess
import io.github.muzammilawan.clipharbor.feature.downloads.DownloadsScreen
import io.github.muzammilawan.clipharbor.feature.home.HomePhase
import io.github.muzammilawan.clipharbor.feature.home.HomeScreen
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.feature.home.YtdlpTheme
import io.github.muzammilawan.clipharbor.feature.library.LibraryScreen
import io.github.muzammilawan.clipharbor.feature.shell.AppDestination
import io.github.muzammilawan.clipharbor.feature.shell.AppShell
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.UserAction
import io.github.muzammilawan.clipharbor.publish.LibraryCatalog
import io.github.muzammilawan.clipharbor.publish.LibraryItem
import io.github.muzammilawan.clipharbor.publish.LibraryKind
import io.github.muzammilawan.clipharbor.publish.MediaStoreLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single activity: Home / Downloads / Library destinations plus separately accessible local diagnostics.
 * Attaches to the shared task/service session; no process-death recovery beyond the durable queue.
 */
class MainActivity : ComponentActivity() {
    private lateinit var attachment: SharedSessionOwner.Attachment<DownloadSession>
    private val session get() = attachment.value
    private val engine get() = session.engine
    private val logger get() = session.logger
    private val storage get() = session.storage
    private val home get() = session.home
    private val diagnostics get() = session.diagnostics
    private val library by lazy { MediaStoreLibrary(this) }
    private val transferPreferences by lazy { TransferPreferences(this) }
    private var showDiagnostics by mutableStateOf(false)
    private var destination by mutableStateOf(AppDestination.HOME)
    private var wifiOnly by mutableStateOf(false)
    private var libraryItems by mutableStateOf<List<LibraryItem>>(emptyList())
    private var libraryLoading by mutableStateOf(false)
    private var libraryError by mutableStateOf<String?>(null)
    private var libraryQuery by mutableStateOf("")
    private var libraryKind by mutableStateOf<LibraryKind?>(null)
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
        wifiOnly = transferPreferences.constraints().wifiOnly

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
        if (!showDiagnostics) refreshLibrary()
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
        val tasks by home.tasks.collectAsState(initial = emptyList())
        val state = diagnostics
        BackHandler(enabled = showDiagnostics || destination != AppDestination.HOME) {
            logger.action(if (showDiagnostics) UserAction.BACK_TO_DOWNLOADS else UserAction.BACK_TO_HOME, ui.phase.name)
            if (showDiagnostics) showDiagnostics = false else destination = AppDestination.HOME
        }
        if (showDiagnostics) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                OutlinedButton(onClick = {
                    logger.action(UserAction.BACK_TO_DOWNLOADS, ui.phase.name)
                    showDiagnostics = false
                }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Back")
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
            return
        }
        AppShell(
            destination = destination,
            onDestination = { next ->
                logger.action(UserAction.OPEN_DESTINATION, ui.phase.name)
                if (next == AppDestination.LIBRARY) refreshLibrary()
                destination = next
            },
            onDiagnostics = ::openDiagnostics,
            home = {
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
                    },
                    onOpenSite = ::openSourceSite,
                    onResolve = {
                        logger.action(UserAction.FIND_FORMATS, ui.phase.name)
                        if (!state.busy) home.resolve(forceRefresh = home.state.value.phase == HomePhase.CHOOSING)
                    }, onSelectQuality = {
                        logger.action(UserAction.SELECT_QUALITY, ui.phase.name, qualityPixels = it.pixels)
                        home.selectQuality(it)
                    }, onSelectAudio = {
                        logger.action(UserAction.SELECT_AUDIO, ui.phase.name)
                        home.selectAudio(it)
                    }, onDownload = { forceMobile ->
                        logger.action(UserAction.DOWNLOAD, ui.phase.name, qualityPixels = ui.selectedQuality?.pixels)
                        if (!state.busy) home.download(forceMobile)
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
                    onDiagnostics = ::openDiagnostics,
                    recent = LibraryCatalog.recent(libraryItems, 3),
                    onOpenRecent = { item ->
                        openSaved(PublishedMedia(item.uri, item.name, item.location, item.mimeType))
                    },
                    onSeeAllLibrary = {
                        destination = AppDestination.LIBRARY
                        refreshLibrary()
                    },
                    durableQueue = home.hasDurableQueue,
                    onStageLink = { if (!state.busy) home.stageCurrentLink() },
                    onSelectExactVideo = { id -> if (!state.busy) home.selectExactVideo(id) },
                )
            },
            downloads = {
                DownloadsScreen(
                    tasks = tasks,
                    enabled = !state.busy,
                    progress = if (ui.phase == HomePhase.DOWNLOADING) ui.progress else null,
                    wifiOnly = wifiOnly,
                    onWifiOnlyChange = { enabled ->
                        logger.action(UserAction.SET_WIFI_ONLY, ui.phase.name, enabled = enabled)
                        wifiOnly = enabled
                        transferPreferences.setWifiOnly(enabled)
                    },
                    onReview = { id, accepted -> if (!state.busy) home.reviewTask(id, accepted) },
                    onRemove = { id -> if (!state.busy) home.removeTask(id) },
                    onOpen = ::openSaved,
                )
            },
            library = {
                LibraryScreen(
                    items = libraryItems,
                    loading = libraryLoading,
                    error = libraryError,
                    query = libraryQuery,
                    kind = libraryKind,
                    onQueryChange = {
                        logger.action(UserAction.SEARCH_LIBRARY, ui.phase.name)
                        libraryQuery = it
                    },
                    onKindChange = {
                        logger.action(UserAction.FILTER_LIBRARY, ui.phase.name)
                        libraryKind = it
                    },
                    onRefresh = {
                        logger.action(UserAction.REFRESH_LIBRARY, ui.phase.name)
                        refreshLibrary()
                    },
                    onOpen = ::openLibraryItem,
                    onShare = ::shareLibraryItem,
                    onDelete = ::deleteLibraryItem,
                )
            },
        )
    }

    private fun openDiagnostics() {
        logger.action(UserAction.OPEN_DIAGNOSTICS, home.state.value.phase.name)
        diagnostics.engineLine = "engine: " + engine.info().headerLine()
        diagnostics.logTail = logger.snapshot()
        diagnostics.verbose = logger.verbose
        showDiagnostics = true
    }

    private fun refreshLibrary() {
        if (libraryLoading) return
        libraryLoading = true
        libraryError = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { library.list() } }
            result.onSuccess { libraryItems = it }.onFailure {
                logger.event('W', "library_list_failed", stage = "UI", fields = mapOf("error" to it.javaClass.simpleName))
                libraryError = "Could not read saved files. Check that the Downloads folder is available, then refresh."
            }
            libraryLoading = false
        }
    }

    private fun openLibraryItem(item: LibraryItem) {
        logger.action(UserAction.OPEN_SAVED_FILE, home.state.value.phase.name)
        if (!launchViewer(item.uri, item.mimeType)) {
            libraryError = "No compatible viewer opened. Find the file in ${item.location}."
        }
    }

    private fun shareLibraryItem(item: LibraryItem) {
        logger.action(UserAction.SHARE_SAVED_FILE, home.state.value.phase.name)
        val uri = item.uri.toUri()
        if (uri.scheme != "content") return
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share saved file"))
            logger.event('I', "library_share_launched", stage = "UI")
        }.onFailure {
            logger.event('W', "library_share_failed", stage = "UI", fields = mapOf("error" to it.javaClass.simpleName))
            libraryError = "No app accepted this file. It is still saved in the Library."
        }
    }

    private fun deleteLibraryItem(item: LibraryItem) {
        logger.action(UserAction.DELETE_SAVED_FILE, home.state.value.phase.name)
        libraryError = null
        lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) { library.delete(item) }
            logger.event('I', "library_delete", stage = "UI", fields = mapOf("removed" to removed.toString()))
            if (removed) {
                libraryItems = libraryItems.filterNot { it.uri == item.uri }
            } else {
                libraryError = "Android did not confirm the delete. The file may still be in ${item.location}."
            }
        }
    }

    private fun openSourceSite(site: SiteShortcut) {
        logger.action(UserAction.OPEN_SOURCE_SITE, home.state.value.phase.name)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, site.homeUrl.toUri()))
            logger.event('I', "open_site_launched", stage = "UI", fields = mapOf("site" to site.id))
        }.onFailure {
            logger.event('W', "open_site_failed", stage = "UI", fields = mapOf("site" to site.id,
                "error" to it.javaClass.simpleName))
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
        destination = AppDestination.HOME
        val text = if (intent.type == "text/plain") runCatching {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }.getOrNull() else null
        // Never read binary streams, grant URIs, automation extras or custom engine options.
        home.receiveShared(text?.take(MediaEntry.MAX_INPUT_CHARS + 1)?.toString().orEmpty())
    }

    private fun openSaved(saved: PublishedMedia) {
        logger.action(UserAction.OPEN_SAVED_FILE, home.state.value.phase.name)
        if (saved.uri.toUri().scheme != "content") return
        if (!launchViewer(saved.uri, saved.mimeType)) {
            diagnostics.status = "No compatible viewer opened. Find the file in ${saved.location}."
            showDiagnostics = true
        }
    }

    /** Opens a content URI in whatever viewer the phone has; returns false instead of claiming success. */
    private fun launchViewer(uri: String, mimeType: String): Boolean {
        val parsed = uri.toUri()
        if (parsed.scheme != "content") return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(parsed, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            logger.event('I', "open_file_launched", stage = "UI")
            true
        }.getOrElse {
            logger.event('W', "open_file_failed", stage = "UI", fields = mapOf("error" to it.javaClass.simpleName))
            false
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
