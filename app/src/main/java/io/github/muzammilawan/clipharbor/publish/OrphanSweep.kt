package io.github.muzammilawan.clipharbor.publish

/** A `Download/YTDLP` MediaStore row that is still marked pending, read by the platform adapter. */
data class PendingRow(val id: Long, val addedAtSeconds: Long, val relativePath: String)

/** A staging child directory: name plus the platform mtime, so the rule stays testable off-device. */
data class StagingCandidate(val name: String, val lastModifiedMs: Long)

data class SweepPlan(val pendingRowIds: List<Long>, val stagingDirectories: List<String>) {
    val isEmpty: Boolean get() = pendingRowIds.isEmpty() && stagingDirectories.isEmpty()
}

/**
 * Startup sweep rules (Phase 2B). Only our own folder and only clearly abandoned work is selected:
 * - a pending MediaStore row must be inside `Download/YTDLP`, have a real timestamp and be older than [PENDING_GRACE_MS];
 * - a staging directory must have a real mtime and be older than [STAGING_GRACE_MS].
 * Nothing is deleted here; [MediaStoreSweep] performs the deletes and re-checks pending state at delete time.
 */
object OrphanSweep {
    const val PENDING_GRACE_MS = 60L * 60 * 1000
    const val STAGING_GRACE_MS = 24L * 60 * 60 * 1000
    const val OWN_FOLDER_PREFIX = "Download/YTDLP"

    fun plan(
        rows: List<PendingRow>,
        staging: List<StagingCandidate>,
        nowMs: Long,
        pendingGraceMs: Long = PENDING_GRACE_MS,
        stagingGraceMs: Long = STAGING_GRACE_MS,
    ): SweepPlan {
        if (nowMs <= 0) return SweepPlan(emptyList(), emptyList())
        val ids = rows.asSequence()
            .filter { it.addedAtSeconds > 0 && inOwnFolder(it.relativePath) }
            .filter { nowMs - it.addedAtSeconds * 1000 > pendingGraceMs }
            .map { it.id }
            .sorted()
            .toList()
        val dirs = staging.asSequence()
            .filter { it.name.isNotBlank() && it.lastModifiedMs > 0 }
            .filter { nowMs - it.lastModifiedMs > stagingGraceMs }
            .map { it.name }
            .sorted()
            .toList()
        return SweepPlan(ids, dirs)
    }

    /** Accepts `Download/YTDLP` or `Download/YTDLP/...`, never a sibling like `Download/YTDLP-extra`. */
    private fun inOwnFolder(path: String): Boolean =
        path == OWN_FOLDER_PREFIX || path.startsWith("$OWN_FOLDER_PREFIX/")
}
