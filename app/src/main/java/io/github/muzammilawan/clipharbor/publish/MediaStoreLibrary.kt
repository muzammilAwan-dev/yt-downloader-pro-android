package io.github.muzammilawan.clipharbor.publish

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.muzammilawan.clipharbor.AppInfo
import java.io.File

/**
 * Read-only view of files this app published, plus an explicit delete.
 *
 * API 29+: MediaStore `Download/YTDLP` rows (the same folder [MediaStorePublisher] writes).
 * API 24-28: the app-specific external Downloads folder, which is where legacy publication goes.
 * Never queries the whole device library.
 */
class MediaStoreLibrary(private val context: Context) {
    fun list(): List<LibraryItem> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        listMediaStore()
    } else {
        listLegacy()
    }

    /** Returns true only when the row/file itself was removed; callers must not claim more. */
    fun delete(item: LibraryItem): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.startsWith("content://")) {
            context.contentResolver.delete(item.uri.toUri(), null, null) == 1
        } else {
            val directory = legacyDirectory() ?: return false
            File(directory, item.name).takeIf { it.isFile }?.delete() == true
        }
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listMediaStore(): List<LibraryItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val arguments = arrayOf("${MediaStorePublisher.RELATIVE_PATH}%")
        val order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, arguments, order,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            buildList {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn),
                    )
                    add(LibraryItem(
                        uri = uri.toString(),
                        name = cursor.getString(nameColumn) ?: "Saved media",
                        mimeType = cursor.getString(mimeColumn) ?: "application/octet-stream",
                        sizeBytes = cursor.getLong(sizeColumn),
                        addedAt = cursor.getLong(addedColumn),
                        location = cursor.getString(pathColumn) ?: MediaStorePublisher.RELATIVE_PATH,
                    ))
                }
            }
        }.orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun listLegacy(): List<LibraryItem> {
        val directory = legacyDirectory() ?: return emptyList()
        val provider = "${context.packageName}.fileprovider"
        return directory.listFiles()?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val uri = runCatching { FileProvider.getUriForFile(context, provider, file).toString() }.getOrNull()
                    ?: return@mapNotNull null
                LibraryItem(
                    uri = uri,
                    name = file.name,
                    mimeType = mimeFor(file),
                    sizeBytes = file.length(),
                    addedAt = file.lastModified() / 1000,
                    location = "app-specific Downloads/${AppInfo.DISPLAY_NAME}",
                )
            }
            ?.toList()
            .orEmpty()
    }

    private fun legacyDirectory(): File? =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, AppInfo.DISPLAY_NAME)

    private fun mimeFor(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}
