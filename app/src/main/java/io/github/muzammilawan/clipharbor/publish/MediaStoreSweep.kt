package io.github.muzammilawan.clipharbor.publish

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Startup sweep adapter for [OrphanSweep]. Deleting a pending row re-checks `IS_PENDING=1` at delete time, so a row
 * committed between the query and the delete survives. Staging directories are removed only inside `files/staging`.
 */
class MediaStoreSweep(private val context: Context) {
    data class Result(val removedRows: Int, val removedStagingDirectories: Int, val failed: Boolean)

    fun sweep(nowMs: Long = System.currentTimeMillis()): Result {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val root = File(context.filesDir, "staging")
        val staging = root.listFiles()?.filter { it.isDirectory }
            ?.map { StagingCandidate(it.name, it.lastModified()) }.orEmpty()
        val plan = OrphanSweep.plan(if (q) pendingRows() else emptyList(), staging, nowMs)
        val rows = if (q) deletePendingRows(plan.pendingRowIds) else RowOutcome(0, false)
        var removedDirs = 0
        var failed = rows.failed
        plan.stagingDirectories.forEach { name ->
            val directory = File(root, name)
            val outcome = runCatching { directory.parentFile == root && directory.deleteRecursively() }
            if (outcome.isFailure) failed = true
            if (outcome.getOrDefault(false)) removedDirs++
        }
        return Result(rows.removed, removedDirs, failed)
    }

    private data class RowOutcome(val removed: Int, val failed: Boolean)

    /** MediaStore.Downloads needs API 29; API 24-28 publishes to the app folder only, so there is nothing to sweep. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deletePendingRows(ids: List<Long>): RowOutcome {
        var removed = 0
        var failed = false
        ids.forEach { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            val outcome = runCatching {
                context.contentResolver.delete(uri, "${MediaStore.MediaColumns.IS_PENDING}=1", null)
            }
            if (outcome.isFailure) failed = true
            if (outcome.getOrDefault(0) == 1) removed++
        }
        return RowOutcome(removed, failed)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun pendingRows(): List<PendingRow> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.MediaColumns.IS_PENDING}=1 AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf("${OrphanSweep.OWN_FOLDER_PREFIX}/%"),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            buildList {
                while (cursor.moveToNext()) {
                    add(PendingRow(
                        id = cursor.getLong(idColumn),
                        addedAtSeconds = cursor.getLong(addedColumn),
                        relativePath = cursor.getString(pathColumn) ?: "",
                    ))
                }
            }
        }.orEmpty()
    }
}
