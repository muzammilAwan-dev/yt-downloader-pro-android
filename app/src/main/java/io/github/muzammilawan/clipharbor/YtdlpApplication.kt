package io.github.muzammilawan.clipharbor

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.MediaEntry
import io.github.muzammilawan.clipharbor.core.SharedSessionOwner
import io.github.muzammilawan.clipharbor.diagnostics.AndroidNetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.AndroidStorageProbe
import io.github.muzammilawan.clipharbor.diagnostics.DiagnosticsState
import io.github.muzammilawan.clipharbor.diagnostics.NetworkProbe
import io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory
import io.github.muzammilawan.clipharbor.diagnostics.SelfTestRunner
import io.github.muzammilawan.clipharbor.diagnostics.StorageProbe
import io.github.muzammilawan.clipharbor.download.AppDatabase
import io.github.muzammilawan.clipharbor.download.DownloadService
import io.github.muzammilawan.clipharbor.download.ForegroundAdmission
import io.github.muzammilawan.clipharbor.download.TaskRepository
import io.github.muzammilawan.clipharbor.engine.YoutubeDlEngine
import io.github.muzammilawan.clipharbor.feature.home.HomeController
import io.github.muzammilawan.clipharbor.feature.home.HomeObserver
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.log.AppLogger
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.RotatingLog
import io.github.muzammilawan.clipharbor.publish.MediaStorePublisher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-local session and service wiring. No durable queue processing yet; persistence is a per-task side effect. */
class YtdlpApplication : Application() {
    private val engine: Engine by lazy { YoutubeDlEngine(this) }
    private val storage: StorageProbe by lazy { AndroidStorageProbe(this) }
    private val network: NetworkProbe by lazy { AndroidNetworkProbe() }
    private val database: AppDatabase by lazy { AppDatabase.build(this) }
    private val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    // One flushed logger per process, so overlapping session cleanup cannot close a newer log writer.
    private val logger: AppLogger by lazy {
        AppLogger(this).also { log ->
            log.startSession(DiagnosticReport.header(
                ReportContextFactory.build(this, engine.info(), log.verbose, storage.freeSpaceMb()),
            ))
            log.event('I', "app_start", stage = "APP",
                fields = mapOf("build" to BuildConfig.BUILD_TYPE, "version" to BuildConfig.VERSION_NAME))
        }
    }

    val sessions: SharedSessionOwner<DownloadSession> by lazy {
        SharedSessionOwner(Dispatchers.Main.immediate, create = { scope ->
            DownloadSession(this, scope, engine, logger, storage, network, foreground, taskRepository)
        }, stop = DownloadSession::close)
    }

    val foreground: ForegroundAdmission by lazy {
        val main = Handler(Looper.getMainLooper())
        ForegroundAdmission(retain = { task ->
            val attached = sessions.attach()
            object : ForegroundAdmission.Owner {
                override fun timeout() { attached.value.home.onForegroundTimeout(task) {} }
                override fun cancel() { attached.value.home.cancel() }
                override fun close() { attached.close() }
            }
        }, start = { task ->
            ContextCompat.startForegroundService(this, DownloadService.intent(this, task))
        }, stopPending = { stopService(DownloadService.intent(this)) }, dispatch = { action -> main.post { action() } })
    }

    internal fun foregroundEvent(event: String) { logger.event('I', event, stage = "FGS") }
}

/** Home, diagnostics and publication share a session scope without retaining an Activity. */
class DownloadSession(
    context: Context,
    scope: CoroutineScope,
    val engine: Engine,
    val logger: AppLogger,
    val storage: StorageProbe,
    private val network: NetworkProbe,
    foreground: DownloadForeground,
    val taskRepository: TaskRepository,
) {
    private val appContext = context.applicationContext
    private val publisher = MediaStorePublisher(appContext)
    val diagnostics = DiagnosticsState()
    @Volatile private var closed = false

    val home = HomeController(engine, scope, File(appContext.filesDir, "staging"), publish = { file ->
        publishMedia(file, mimeTypeFor(file))
    }, observer = object : HomeObserver {
        override fun event(task: String?, stage: String, event: String, fields: Map<String, String>) {
            if (closed) return
            logger.event('I', event, task = task, stage = stage, fields = fields,
                message = if (event == "engine_init") engine.info().headerLine() else null)
        }
        override fun failure(task: String?, error: DownloadError) {
            if (!closed) logFailure(task, error)
        }
        override fun line(task: String, stage: String, line: String) {
            if (closed) return
            logger.write(RotatingLog.timestamp(System.currentTimeMillis()) +
                " V task=$task stage=$stage event=ytdlp " + line)
        }
    }, foreground = foreground, publishAudio = { file, mime -> publishMedia(file, mime) },
        repository = taskRepository,
        freeSpaceMb = { storage.freeSpaceMb() })

    init { logger.event('I', "session_start", stage = "APP") }

    suspend fun runSelfTest(taskId: String) {
        val state = diagnostics
        if (closed || state.busy || home.state.value.busy) return
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
            logger.event('I', "done", task = taskId, stage = "SELFTEST", fields = mapOf(
                "failures" to report.failures.toString(), "ms" to (System.currentTimeMillis() - started).toString()))
            state.report = report.reportText()
            state.status = report.summaryLine()
            state.engineLine = "engine: " + engine.info().headerLine()
        } finally {
            state.busy = false
            state.logTail = logger.snapshot()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        home.close()
        logger.event('I', "session_stop", stage = "APP")
    }

    private fun logFailure(taskId: String?, error: DownloadError) {
        logger.event('E', "fail", task = taskId, stage = error.stage.name, fields = buildMap {
            put("code", error.code.name)
            put("retryable", error.retryable.toString())
            error.exitCode?.let { put("exit", it.toString()) }
        }, message = error.detail)
        error.stderrTail.takeLast(200).forEach { line ->
            logger.write(RotatingLog.timestamp(System.currentTimeMillis()) +
                " E task=$taskId stage=${error.stage} event=ytdlp " + line)
        }
    }

    private suspend fun publishMedia(file: File, mime: String): PublishedMedia {
        val uri = withContext(Dispatchers.IO) { publisher.publish(file, mime).getOrThrow() }
        return PublishedMedia(uri.toString(), file.name,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Downloads/${AppInfo.DISPLAY_NAME}"
            else "App-specific downloads (removed if the app is uninstalled)", mime)
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> "video/mp4"
    }
}
