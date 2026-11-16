package io.github.muzammilawan.clipharbor.publish

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import io.github.muzammilawan.clipharbor.core.StorageSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** No storage permission: checks the filesystem containing the existing fixed publication destination. */
class AndroidStorageSpace(private val context: Context) {
    suspend fun read(): StorageSpace = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val destination = if (Build.VERSION.SDK_INT >= 29) Environment.getExternalStorageDirectory()
            else context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        StorageSpace(StatFs(context.filesDir.absolutePath).availableBytes, StatFs(destination.absolutePath).availableBytes)
    }
}
