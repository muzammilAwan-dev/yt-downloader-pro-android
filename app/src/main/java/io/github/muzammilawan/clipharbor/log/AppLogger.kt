package io.github.muzammilawan.clipharbor.log

import android.content.Context
import io.github.muzammilawan.clipharbor.BuildConfig
import java.io.File

/**
 * App-wide logger (docs/12 §4): rotating private file always, plus a public MediaStore copy in debug builds only.
 *
 * Full-session shares use [SessionLog]; the rotating files are only the bounded preview/history.
 * Every sink redacts before writing; sharing never exposes a live file that keeps changing.
 */
class AppLogger(context: Context) {
    private val appContext = context.applicationContext
    private val rotating = RotatingLog(File(appContext.filesDir, LOG_DIR))
    private val session = SessionLog(File(appContext.filesDir, "$LOG_DIR/session"))
    private var sessionHeader = ""
    private val mirror: MediaStoreLogMirror? =
        if (BuildConfig.DEBUG) MediaStoreLogMirror(appContext) else null

    @Volatile
    var verbose: Boolean = BuildConfig.DEBUG

    @Volatile
    var publicMirrorActive: Boolean = false
        private set

    @Synchronized
    fun startSession(header: String) {
        sessionHeader = header
        // The rotating log stamps this header at the top of every file it opens from now on.
        rotating.fileHeader = header
        session.start(header)
        runCatching { rotating.ensureOpen() }
        publicMirrorActive = mirror?.start(header) == true
    }

    /** One greppable event per line: timestamp, level, task, stage, event, fields. */
    fun event(
        level: Char,
        event: String,
        task: String? = null,
        stage: String? = null,
        fields: Map<String, String> = emptyMap(),
        message: String? = null,
        verboseOnly: Boolean = false,
    ) {
        if (verboseOnly && !verbose) return
        val line = buildString {
            append(RotatingLog.timestamp(System.currentTimeMillis()))
            append(' ').append(level)
            task?.let { append(" task=").append(it) }
            stage?.let { append(" stage=").append(it) }
            append(" event=").append(event)
            fields.forEach { (key, value) ->
                append(' ').append(key).append('=').append(value.replace(' ', '_'))
            }
            message?.let { append(" msg=\"").append(it.replace('"', '\'').take(500)).append('"') }
        }
        write(line)
    }

    @Synchronized
    fun write(line: String) {
        session.append(line)
        runCatching { rotating.append(line) }
        mirror?.append(line)
    }

    fun action(action: UserAction, phase: String? = null, qualityPixels: Int? = null, enabled: Boolean? = null) {
        write(action.line(System.currentTimeMillis(), phase, qualityPixels, enabled))
    }

    /** Share action and exact cutoff are captured together, before any asynchronous IO. */
    @Synchronized
    fun captureShare(phase: String?): SessionLog.Cutoff {
        action(UserAction.SHARE_LOG, phase)
        return session.capture()
    }

    fun exportSession(cutoff: SessionLog.Cutoff): File = session.export(cutoff)

    /** Last [maxLines] lines across the newest log files, for the on-screen preview. */
    fun snapshot(maxLines: Int = 200): String {
        val lines = mutableListOf<String>()
        for (file in rotating.files()) {
            if (lines.size >= maxLines) break
            val fileLines = runCatching { file.readLines() }.getOrDefault(emptyList())
            lines.addAll(0, fileLines.takeLast(maxLines - lines.size))
        }
        return lines.joinToString("\n")
    }

    fun logsDir(): File = File(appContext.filesDir, LOG_DIR)

    fun newestLogFile(): File? = rotating.files().firstOrNull()

    @Synchronized
    fun clear() {
        rotating.clear()
        session.clear()
        mirror?.close()
        publicMirrorActive = mirror?.start(sessionHeader + "\nsession_scope=since_clear") == true
        action(UserAction.CLEAR_LOGS)
    }

    @Synchronized
    fun close() {
        rotating.close()
        session.close()
        mirror?.close()
        publicMirrorActive = false
    }

    companion object {
        const val LOG_DIR = "logs"
    }
}
