package io.github.muzammilawan.clipharbor.log

import android.content.Context
import io.github.muzammilawan.clipharbor.BuildConfig
import java.io.File

/**
 * App-wide logger (docs/12 §4): rotating private file always, plus a public MediaStore copy in debug builds only.
 *
 * Lines are redacted by [RotatingLog] and [MediaStoreLogMirror] before they reach disk, as a second line of defence.
 */
class AppLogger(context: Context) {
    private val appContext = context.applicationContext
    private val rotating = RotatingLog(File(appContext.filesDir, LOG_DIR))
    private val mirror: MediaStoreLogMirror? =
        if (BuildConfig.DEBUG) MediaStoreLogMirror(appContext) else null

    @Volatile
    var verbose: Boolean = BuildConfig.DEBUG

    @Volatile
    var publicMirrorActive: Boolean = false
        private set

    fun startSession(header: String) {
        // The rotating log stamps this header at the top of every file it opens from now on.
        rotating.fileHeader = header
        rotating.ensureOpen()
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

    fun write(line: String) {
        rotating.append(line)
        mirror?.append(line)
    }

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

    fun clear() {
        rotating.clear()
        mirror?.close()
    }

    fun close() {
        rotating.close()
        mirror?.close()
    }

    companion object {
        const val LOG_DIR = "logs"
    }
}
