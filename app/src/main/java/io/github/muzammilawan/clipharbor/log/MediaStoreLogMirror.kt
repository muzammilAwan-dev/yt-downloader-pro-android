package io.github.muzammilawan.clipharbor.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.muzammilawan.clipharbor.AppInfo
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only public copy of the session log in `Download/YTDLP-logs/` via MediaStore, so the owner can attach it
 * without opening the app (docs/13 §4). No storage permission is needed on API 29+; on API 24-28 this is skipped and
 * only "Share log" works.
 *
 * All methods are best-effort: a failure here must never break a download, so nothing throws.
 */
class MediaStoreLogMirror(private val context: Context) {
    private var uri: Uri? = null
    private var stream: OutputStream? = null

    @Synchronized
    fun start(header: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val name = AppInfo.LOG_FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val inserted = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            uri = inserted
            stream = context.contentResolver.openOutputStream(inserted, "wa")
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(inserted, publish, null, null)
            append(header)
            prune()
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun append(text: String) {
        val out = stream ?: return
        runCatching {
            out.write(LogRedactor.redactLine(text).toByteArray(Charsets.UTF_8))
            out.write('\n'.code)
            out.flush()
        }
    }

    @Synchronized
    fun close() {
        runCatching { stream?.flush() }
        runCatching { stream?.close() }
        stream = null
        uri = null
    }

    /** Keeps the newest [MAX_SESSIONS] session files (including this one). */
    private fun prune() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val currentId = uri?.lastPathSegment?.toLongOrNull()
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            val args = arrayOf(RELATIVE_PATH)
            val order = "${MediaStore.MediaColumns.DATE_ADDED} ASC"
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                order,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val ids = buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(idColumn))
                }
                val removable = ids.filter { it != currentId }
                removable.dropLast(MAX_SESSIONS - 1).forEach { id ->
                    runCatching {
                        context.contentResolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "${MediaStore.MediaColumns._ID}=?",
                            arrayOf(id.toString()),
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val RELATIVE_PATH = AppInfo.LOG_RELATIVE_PATH
        const val MAX_SESSIONS = 5
    }
}
