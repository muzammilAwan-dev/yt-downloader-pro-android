package io.github.muzammilawan.clipharbor.core

import java.io.Closeable
import java.io.File
import java.io.IOException

/** Sensitive, single-use extraction input. Not a data class, not serializable, and never part of task history. */
class ResolvedSnapshot internal constructor(
    private var sourceUrl: String?,
    private var taskId: String?,
    json: String,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : Closeable {
    private var payload: ByteArray? = json.toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_BYTES) { "Resolved input exceeds size limit" }
    }
    private val createdAt = clockMs()

    /** Runs inside the blocking engine worker; deletes the input only after that worker releases it. */
    fun <T> withFile(url: String, processId: String, directory: File, block: (File) -> T): T {
        val bytes = synchronized(this) {
            val available = payload ?: throw SnapshotUnavailableException()
            payload = null
            val age = clockMs() - createdAt
            val valid = sourceUrl == url && taskId == processId && age in 0..MAX_AGE_MS
            sourceUrl = null
            taskId = null
            if (!valid) {
                available.fill(0)
                throw SnapshotUnavailableException()
            }
            available
        }
        var file: File? = null
        try {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create private resolved-input directory" }
            file = File.createTempFile(PREFIX, SUFFIX, directory)
            file.outputStream().use { it.write(bytes) }
            bytes.fill(0)
            return block(file)
        } finally {
            bytes.fill(0)
            file?.let {
                if (it.exists() && !it.delete()) throw IOException("Cannot delete private resolved input")
            }
        }
    }

    @Synchronized
    override fun close() {
        payload?.fill(0)
        payload = null
        sourceUrl = null
        taskId = null
    }

    override fun toString(): String = "ResolvedSnapshot(<private>)"

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_AGE_MS = 5 * 60 * 1000L
        private const val PREFIX = "clipharbor-resolved-"
        private const val SUFFIX = ".info.json"

        /** Call once at process startup, before any workers start. Never traverses directories or deletes other files. */
        fun cleanOrphans(directory: File) {
            if (!directory.exists()) return
            val files = directory.listFiles() ?: throw IOException("Cannot inspect private resolved inputs")
            files.filter { it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) && it.isFile }.forEach {
                if (!it.delete()) throw IOException("Cannot clean private resolved input")
            }
        }
    }
}

class SnapshotUnavailableException : IllegalStateException("Resolved input unavailable; resolve again")

/** Remove old selected-format/output state while preserving the full format catalog for the current selector. */
object ReplayInfo {
    fun fieldsToDiscard(keys: Set<String>, hasFormatCatalog: Boolean): Set<String> {
        require(hasFormatCatalog) { "Resolved input has no reusable format catalog" }
        return keys.filterTo(mutableSetOf()) { it.startsWith('_') || it in SELECTED_FIELDS }
    }

    private val SELECTED_FIELDS = setOf(
        "requested_formats", "requested_downloads", "filename", "filepath", "infojson_filename",
        "url", "manifest_url", "manifest_stream_number", "fragment_base_url", "fragments", "http_headers",
        "format", "format_id", "format_note", "ext", "protocol", "container", "vcodec", "acodec",
        "width", "height", "resolution", "fps", "filesize", "filesize_approx", "tbr", "abr", "vbr",
        "asr", "audio_channels", "quality", "source_preference", "preference", "language_preference",
        "dynamic_range", "stretched_ratio", "downloader_options", "cookies",
    )
}
