package io.github.muzammilawan.clipharbor.log

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Rotating, app-private text log: `app-YYYYMMDD-HHmmss.log`, at most [maxFiles] files of [maxBytesPerFile] bytes.
 *
 * Pure JVM (takes a [dir] and a clock) so rotation is unit-tested with a temporary directory (docs/12 §4).
 * Every line is redacted before it hits the file, as a second line of defence behind the logger facade.
 */
class RotatingLog(
    private val dir: File,
    private val maxBytesPerFile: Long = DEFAULT_MAX_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(maxBytesPerFile >= 64) { "maxBytesPerFile must leave room for a header and event" }
        require(maxFiles > 0) { "maxFiles must be positive" }
    }

    private var current: File? = null
    private var writer: BufferedWriter? = null

    /**
     * Written at the top of **every** new file, so a log copied out mid-session is still self-explaining (the Phase 0
     * device run produced shared copies without a header).
     */
    @Volatile
    var fileHeader: String? = null

    /** Opens the current file (writing [fileHeader] first) so "Share log" works before the first event. */
    @Synchronized
    fun ensureOpen() {
        openIfNeeded("")
    }

    @Synchronized
    fun append(line: String) {
        val header = headerText()
        val availableBytes = maxBytesPerFile - header.toByteArray(Charsets.UTF_8).size - 1
        val safe = limitUtf8(LogRedactor.redactLine(line), availableBytes)
        openIfNeeded(safe, header)
        val w = writer ?: return
        w.write(safe)
        w.write("\n")
        w.flush()
    }

    @Synchronized
    fun close() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        current = null
    }

    @Synchronized
    fun currentFile(): File? = current?.takeIf { it.exists() }

    /** Newest first. */
    @Synchronized
    fun files(): List<File> {
        val all = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(".log") }
            ?: return emptyList()
        return all.sortedByDescending { it.lastModified() }
    }

    @Synchronized
    fun clear() {
        close()
        files().forEach { runCatching { it.delete() } }
    }

    private fun openIfNeeded(line: String, header: String = headerText()) {
        val existing = current
        val tooBig = existing != null && existing.length() + line.toByteArray(Charsets.UTF_8).size + 1 > maxBytesPerFile
        if (writer != null && existing != null && !tooBig) return
        close()
        if (!dir.exists()) dir.mkdirs()
        current = nextFile()
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(current!!, true), Charsets.UTF_8))
        writer!!.write(header)
        writer!!.flush()
        prune()
    }

    private fun headerText(): String {
        val header = fileHeader?.trim().orEmpty()
        if (header.isEmpty()) return ""
        val safe = header.lineSequence().joinToString("\n") { LogRedactor.redactLine(it) }
        // Even an unexpectedly large report must leave space for at least one event.
        return limitUtf8(safe, maxBytesPerFile / 2 - 1) + "\n"
    }

    private fun limitUtf8(text: String, maxBytes: Long): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        val suffix = " [truncated]"
        var end = (maxBytes - suffix.toByteArray(Charsets.UTF_8).size).toInt()
        // Do not cut a multi-byte character in half.
        while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8) + suffix
    }

    /** `app-YYYYMMDD-HHmmss.log`, then `app-YYYYMMDD-HHmmss-2.log`, … so two copies are never confused. */
    private fun nextFile(): File {
        val now = clock()
        val stamp = dayStamp(now)
        val time = timeStamp(now)
        var candidate = File(dir, "$FILE_PREFIX-$stamp-$time.log")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$FILE_PREFIX-$stamp-$time-$index.log")
            index++
        }
        return candidate
    }

    /** Keeps the newest [maxFiles] files, current one included. */
    private fun prune() {
        val keep = current
        val others = files().filter { it != keep }
        val allowedOthers = (maxFiles - 1).coerceAtLeast(0)
        others.drop(allowedOthers).forEach { runCatching { it.delete() } }
    }

    private fun dayStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun timeStamp(millis: Long): String =
        SimpleDateFormat("HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 2L * 1024 * 1024
        const val DEFAULT_MAX_FILES: Int = 5
        const val FILE_PREFIX: String = "app"

        fun timestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))
    }
}
