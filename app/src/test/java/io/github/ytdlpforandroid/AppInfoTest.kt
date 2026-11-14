package io.github.ytdlpforandroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the naming rules from AGENTS.md and docs/11 without needing a device. */
class AppInfoTest {

    @Test
    fun displayNameIsNotEmpty() {
        assertTrue(AppInfo.DISPLAY_NAME.isNotBlank())
    }

    @Test
    fun displayNameUsesNoForbiddenBrand() {
        assertFalse(
            "Display name '${AppInfo.DISPLAY_NAME}' reuses a prohibited brand",
            AppInfo.hasForbiddenBrand(AppInfo.DISPLAY_NAME),
        )
    }

    @Test
    fun forbiddenBrandCheckIsCaseInsensitiveAndCatchesRealNames() {
        // Positive controls: these really must be rejected.
        assertTrue(AppInfo.hasForbiddenBrand("SnapTube"))
        assertTrue(AppInfo.hasForbiddenBrand("YouTube Downloader"))
        assertTrue(AppInfo.hasForbiddenBrand("ytdlnis fork"))
        // Sanity: our own wording is fine.
        assertFalse(AppInfo.hasForbiddenBrand("yt-dlp for Android"))
    }

    @Test
    fun appStaysExplicitlyUnofficial() {
        assertTrue(AppInfo.IS_UNOFFICIAL)
    }
}
