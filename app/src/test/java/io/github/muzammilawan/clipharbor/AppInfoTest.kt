package io.github.muzammilawan.clipharbor

import org.junit.Assert.assertFalse
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
    fun displayNameIsNotEmpty() {
        assertTrue(AppInfo.DISPLAY_NAME.isNotBlank())
    }

    @Test
    fun forbiddenBrandCheckIsCaseInsensitiveAndCatchesRealNames() {
        assertTrue(AppInfo.hasForbiddenBrand("SnapTube"))
        assertTrue(AppInfo.hasForbiddenBrand("YouTube"))
        assertTrue(AppInfo.hasForbiddenBrand("YTDLnis"))
        assertFalse(AppInfo.hasForbiddenBrand("ClipHarbor"))
    }

    @Test
    fun applicationIdMatchesChosenBrand() {
        assertTrue(AppInfo.APPLICATION_ID.startsWith("io.github."))
        assertTrue(AppInfo.APPLICATION_ID.endsWith(".clipharbor"))
        assertFalse(AppInfo.hasForbiddenBrand(AppInfo.APPLICATION_ID))
    }
}
