package io.github.muzammilawan.clipharbor.publish

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.muzammilawan.clipharbor.AppInfo
import java.io.File

/**
 * Publishes a finished file so the phone's gallery/Files app can see it (docs/02 §7).
 *
 * API 29+: MediaStore `Download/YTDLP` with `IS_PENDING` around the copy.
 * API 24-28: the public Downloads folder needs `WRITE_EXTERNAL_STORAGE`, which the permission budget forbids, so the
 * file goes to the app-specific external directory instead and the UI must say so.
 */
class MediaStorePublisher(private val context: Context) {
    fun publish(source: File, mimeType: String = "video/mp4"): Result<Uri> = runCatching {
        require(source.isFile && source.length() > 0) { "source file missing or empty" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(source, mimeType)
        } else {
            publishLegacy(source)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun publishViaMediaStore(source: File, mimeType: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return PublicationFiles.pending(insert = { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }, copy = { uri ->
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> check(input.copyTo(output) == source.length()) { "Publication copy is incomplete" } }
            } ?: error("MediaStore returned no output stream")
        }, commit = { uri ->
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }, discard = { uri -> resolver.delete(uri, null, null) })
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(source: File): Uri {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            AppInfo.DISPLAY_NAME,
        ).apply { mkdirs() }
        val target = PublicationFiles.legacy(source, dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    companion object {
        const val RELATIVE_PATH = AppInfo.DOWNLOAD_RELATIVE_PATH
    }
}
