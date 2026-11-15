package io.github.muzammilawan.clipharbor.log

import java.io.BufferedWriter
import java.io.File
import java.util.Locale

/**
 * Full, redacted current-process journal, separate from the bounded rotating preview.
 * Capture is cheap and synchronous at the Share tap; export copies exactly that byte prefix on IO.
 * No session size cap: preserving the beginning costs disk space in long debugging sessions.
 */
class SessionLog(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    class Cutoff internal constructor(
        internal val owner: SessionLog,
        internal val generation: Long,
        internal val bytes: Long,
        internal val time: Long,
    )

    private val journal = File(dir, "active.txt")
    private val exports = File(dir, "exports")
    private val exportLock = Any()
    private var writer: BufferedWriter? = null
    private var header = ""
    private var generation = 0L
    private var serial = 0L
    private var failed = true

    @Synchronized
    fun start(header: String): Boolean {
        this.header = header.lineSequence().joinToString("\n", transform = LogRedactor::redactLine).trimEnd()
        return reset("since_app_start")
    }

    @Synchronized
    fun append(line: String) {
        try {
            val out = checkNotNull(writer)
            out.write(LogRedactor.redactLine(line))
            out.newLine()
            out.flush()
        } catch (_: Exception) { failed = true }
    }

    @Synchronized
    fun capture(): Cutoff {
        check(!failed && journal.isFile) { "Full session log is unavailable" }
        return Cutoff(this, generation, journal.length(), clock())
    }

    /** Append-only writes may continue while copying; reset invalidates the old generation. */
    fun export(cutoff: Cutoff): File = synchronized(exportLock) {
        val target = synchronized(this) {
            validate(cutoff)
            check(exports.isDirectory || exports.mkdirs()) { "Cannot create log export directory" }
            var candidate: File
            do {
                candidate = File(exports, "session-${cutoff.time}-${(++serial).toString().padStart(12, '0')}.txt")
            } while (candidate.exists())
            candidate
        }
        try {
            journal.inputStream().use { input ->
                target.outputStream().use { output ->
                    var remaining = cutoff.bytes
                    val buffer = ByteArray(8192)
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        check(read > 0) { "Session changed while exporting" }
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                    output.write(("== SNAPSHOT END ==\nsession_complete=true cutoff=" +
                        RotatingLog.timestamp(cutoff.time) + "\n").toByteArray(Charsets.UTF_8))
                }
            }
            synchronized(this) {
                validate(cutoff)
                // Keep five immutable shares, independently of the full active journal.
                exports.listFiles { file -> file.isFile && file.name.startsWith("session-") && file.extension == "txt" }
                    .orEmpty().filter { it != target }
                    .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
                    .drop(4).forEach { it.delete() }
            }
            target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    @Synchronized
    fun clear() {
        reset("since_clear")
        exports.listFiles { file -> file.isFile && file.name.startsWith("session-") && file.extension == "txt" }
            .orEmpty().forEach { it.delete() }
    }

    @Synchronized
    fun close() {
        try { writer?.close() } catch (_: Exception) { failed = true }
        writer = null
    }

    private fun validate(cutoff: Cutoff) {
        check(cutoff.owner === this && cutoff.generation == generation) { "Session changed while exporting" }
    }

    private fun reset(scope: String): Boolean {
        close()
        generation++
        failed = true
        return try {
            check(dir.isDirectory || dir.mkdirs())
            writer = journal.bufferedWriter(Charsets.UTF_8)
            writer!!.write(header + "\nsession_scope=$scope session_start=" + RotatingLog.timestamp(clock()) + "\n")
            writer!!.flush()
            failed = false
            true
        } catch (_: Exception) { close(); false }
    }
}

/** Fixed action names only; there is deliberately no clipboard/link/title payload parameter. */
enum class UserAction {
    EDIT_LINK, PASTE_LINK, FIND_FORMATS, SELECT_QUALITY, DOWNLOAD, CANCEL, RETRY_ENGINE, USE_PENDING_LINK,
    OPEN_SAVED_FILE, OPEN_DIAGNOSTICS, BACK_TO_DOWNLOADS, EDIT_SELFTEST_LINK, RUN_SELF_TEST, SHARE_LOG,
    COPY_SUMMARY, CLEAR_LOGS, SET_VERBOSE, ENABLE_NOTIFICATIONS, SHARE_LINK_RECEIVED,
    ACTIVITY_CREATE, ACTIVITY_RESUME, ACTIVITY_PAUSE, ACTIVITY_STOP, ACTIVITY_DESTROY, NEW_INTENT;

    fun line(time: Long, phase: String?, qualityPixels: Int? = null, enabled: Boolean? = null): String = buildString {
        append(RotatingLog.timestamp(time)).append(" I stage=UI event=").append(name.lowercase(Locale.ROOT))
        phase?.let { append(" phase=").append(it) }
        qualityPixels?.let { append(" qualityPixels=").append(it) }
        enabled?.let { append(" enabled=").append(it) }
    }
}
