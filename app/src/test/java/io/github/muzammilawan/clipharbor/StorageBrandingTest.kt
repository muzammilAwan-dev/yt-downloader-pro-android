package io.github.muzammilawan.clipharbor

import io.github.muzammilawan.clipharbor.log.MediaStoreLogMirror
import io.github.muzammilawan.clipharbor.publish.MediaStorePublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Compile-time constants only: no Android resolver, storage or live engine required. */
class StorageBrandingTest {
    @Test
    fun publisherUsesCurrentBrandWithoutChangingPackageIdentity() {
        assertEquals("Download/YTDLP", MediaStorePublisher.RELATIVE_PATH)
        assertEquals(AppInfo.DOWNLOAD_RELATIVE_PATH, MediaStorePublisher.RELATIVE_PATH)
        assertEquals("io.github.muzammilawan.clipharbor", AppInfo.APPLICATION_ID)
    }

    @Test
    fun publicLogFolderUsesCurrentBrandAndKeepsDownloadAndLogScopesSeparate() {
        assertEquals("Download/YTDLP-logs", MediaStoreLogMirror.RELATIVE_PATH)
        assertEquals(AppInfo.LOG_RELATIVE_PATH, MediaStoreLogMirror.RELATIVE_PATH)
        assertFalse(MediaStoreLogMirror.RELATIVE_PATH == MediaStorePublisher.RELATIVE_PATH)
        assertEquals(5, MediaStoreLogMirror.MAX_SESSIONS)
    }
}
