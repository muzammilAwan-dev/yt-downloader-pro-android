package io.github.muzammilawan.clipharbor.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {
    private fun item(name: String, mime: String, addedAt: Long, bytes: Long = 10) =
        LibraryItem("content://media/$name", name, mime, bytes, addedAt, "Download/YTDLP")

    @Test fun classifiesByMimeTypeOnly() {
        assertEquals(LibraryKind.VIDEO, LibraryCatalog.kindOf("video/mp4"))
        assertEquals(LibraryKind.AUDIO, LibraryCatalog.kindOf("audio/opus"))
        assertEquals(LibraryKind.IMAGE, LibraryCatalog.kindOf("image/jpeg"))
        assertEquals(LibraryKind.OTHER, LibraryCatalog.kindOf("application/octet-stream"))
    }

    @Test fun filtersByNameAndKindThenSortsNewestFirst() {
        val items = listOf(
            item("holiday.mp4", "video/mp4", 10),
            item("Holiday Song.opus", "audio/opus", 30),
            item("notes.mp4", "video/mp4", 20),
        )
        assertEquals(listOf("Holiday Song.opus", "holiday.mp4"),
            LibraryCatalog.visible(items, "holiday", null).map { it.name })
        assertEquals(listOf("notes.mp4", "holiday.mp4"),
            LibraryCatalog.visible(items, "", LibraryKind.VIDEO).map { it.name })
        assertEquals(listOf("Holiday Song.opus"),
            LibraryCatalog.visible(items, "song", LibraryKind.AUDIO).map { it.name })
    }

    @Test fun blankQueryAndNullKindShowEverything() {
        val items = listOf(item("b.mp4", "video/mp4", 1), item("a.opus", "audio/opus", 2))
        assertEquals(listOf("a.opus", "b.mp4"), LibraryCatalog.visible(items, "   ", null).map { it.name })
    }

    @Test fun recentPreviewIsTheNewestSliceOfTheSameOrder() {
        val items = listOf(item("old.mp4", "video/mp4", 1), item("new.opus", "audio/opus", 3), item("mid.mp4", "video/mp4", 2))
        assertEquals(listOf("new.opus", "mid.mp4"), LibraryCatalog.recent(items, 2).map { it.name })
        assertTrue(LibraryCatalog.recent(items, 0).isEmpty())
    }

    @Test fun sizesStayReadableAndLocaleIndependent() {
        assertEquals("0 B", LibraryCatalog.formatBytes(0))
        assertEquals("0 B", LibraryCatalog.formatBytes(-5))
        assertEquals("999 B", LibraryCatalog.formatBytes(999))
        assertEquals("1.0 KB", LibraryCatalog.formatBytes(1024))
        assertEquals("1.5 KB", LibraryCatalog.formatBytes(1536))
        assertEquals("1.4 MB", LibraryCatalog.formatBytes(1_500_000))
        assertEquals("103.4 MB", LibraryCatalog.formatBytes(108_448_544))
    }

    @Test fun durationsAreShownOnlyWhenKnown() {
        assertEquals("0:59", LibraryCatalog.formatDuration(59_999))
        assertEquals("1:05", LibraryCatalog.formatDuration(65_000))
        assertEquals("1:02:05", LibraryCatalog.formatDuration(3_725_000))
        assertEquals(null, LibraryCatalog.formatDuration(null))
        assertEquals(null, LibraryCatalog.formatDuration(0))
        assertEquals(null, LibraryCatalog.formatDuration(-1000))
    }

    @Test fun totalIgnoresNegativeSizes() {
        val items = listOf(item("a", "video/mp4", 1, 5), item("b", "video/mp4", 1, -3))
        assertEquals(5L, LibraryCatalog.totalBytes(items))
        assertTrue(LibraryCatalog.visible(emptyList(), "x", null).isEmpty())
    }
}
