package io.github.muzammilawan.clipharbor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {
    @Test
    fun displayNameUsesNoForbiddenBrand() {
        assertFalse(AppInfo.hasForbiddenBrand(AppInfo.DISPLAY_NAME))
    }

    @Test
    fun appStaysExplicitlyUnofficial() {
        assertTrue(AppInfo.IS_UNOFFICIAL)
        assertTrue(AppInfo.ENGINE_CREDIT.contains("yt-dlp"))
    }

    @Test
    fun displayNameAndExpansionMatchOwnerDecision() {
        assertEquals("YTDLP", AppInfo.DISPLAY_NAME)
        assertEquals("You Tube Downloader Pro", AppInfo.FULL_NAME)
    }

    @Test
    fun forbiddenBrandCheckIsCaseInsensitiveAndCatchesRealNames() {
        assertTrue(AppInfo.hasForbiddenBrand("SnapTube"))
        assertTrue(AppInfo.hasForbiddenBrand("YouTube"))
        assertTrue(AppInfo.hasForbiddenBrand("YTDLnis"))
        assertFalse(AppInfo.hasForbiddenBrand("ClipHarbor"))
        assertFalse(AppInfo.hasForbiddenBrand(AppInfo.DISPLAY_NAME))
    }

    @Test
    fun applicationIdIsPreservedAcrossDisplayRename() {
        assertEquals("io.github.muzammilawan.clipharbor", AppInfo.APPLICATION_ID)
        assertFalse(AppInfo.hasForbiddenBrand(AppInfo.APPLICATION_ID))
    }

    @Test
    fun newOutputNamesUseChosenBrand() {
        assertEquals("Download/YTDLP", AppInfo.DOWNLOAD_RELATIVE_PATH)
        assertEquals("Download/YTDLP-logs", AppInfo.LOG_RELATIVE_PATH)
        assertEquals("ytdlp-log-", AppInfo.LOG_FILE_PREFIX)
    }
}
