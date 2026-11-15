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
 * Rotating, app-private text log: `app-YYYYMMDD.log`, at most [maxFiles] files of [maxBytesPerFile] bytes.
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
    private var current: File? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun append(line: String) {
        val safe = LogRedactor.redactLine(line)
        openIfNeeded(safe)
        val w = writer ?: return
        w.write(safe)
        w.newLine()
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

    private fun openIfNeeded(line: String) {
        val existing = current
        val tooBig = existing != null && existing.length() + line.length + 1 > maxBytesPerFile
        if (writer != null && existing != null && !tooBig) return
        close()
        if (!dir.exists()) dir.mkdirs()
        current = nextFile()
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(current!!, true), Charsets.UTF_8))
        prune()
    }

    private fun nextFile(): File {
        val stamp = dayStamp(clock())
        var candidate = File(dir, "$FILE_PREFIX-$stamp.log")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$FILE_PREFIX-$stamp-$index.log")
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
