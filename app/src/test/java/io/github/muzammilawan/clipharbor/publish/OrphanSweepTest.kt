package io.github.muzammilawan.clipharbor.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrphanSweepTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun row(id: Long, ageMs: Long, path: String = "Download/YTDLP/") =
        PendingRow(id, (now - ageMs) / 1000, path)

    @Test fun abandonedPendingRowsInOurFolderAreSelectedNewestFirstById() {
        val plan = OrphanSweep.plan(
            listOf(row(3, 5 * hour), row(1, 2 * hour), row(2, 30 * 1000)),
            emptyList(), now,
        )
        assertEquals(listOf(1L, 3L), plan.pendingRowIds)
    }

    @Test fun youngRowsAndOtherAppsFoldersAreNeverSelected() {
        val plan = OrphanSweep.plan(
            listOf(row(1, 5 * 60 * 1000), row(2, 5 * hour, "Download/Other/"), row(3, 5 * hour, "Download/YTDLP-extra/")),
            emptyList(), now,
        )
        assertTrue(plan.isEmpty)
    }

    @Test fun stagingDirectoriesUseTheirOwnLongerGrace() {
        val plan = OrphanSweep.plan(
            emptyList(),
            listOf(StagingCandidate("fresh", now - 2 * hour), StagingCandidate("old", now - 2 * day),
                StagingCandidate("no-time", 0), StagingCandidate(" ", now - 2 * day)),
            now,
        )
        assertEquals(listOf("old"), plan.stagingDirectories)
    }

    @Test fun boundaryAgesAreKeptAndMissingClockSelectsNothing() {
        assertEquals(emptyList<Long>(), OrphanSweep.plan(listOf(row(1, OrphanSweep.PENDING_GRACE_MS)),
            listOf(StagingCandidate("exact", now - OrphanSweep.STAGING_GRACE_MS)), now).pendingRowIds)
        assertTrue(OrphanSweep.plan(listOf(row(1, 5 * hour)),
            listOf(StagingCandidate("old", now - 2 * day)), 0).isEmpty)
    }
}
