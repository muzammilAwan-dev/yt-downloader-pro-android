package io.github.muzammilawan.clipharbor

/**
 * Static, platform-free app metadata.
 *
 * Kept free of Android imports so it can be unit-tested on the JVM. The display name was chosen by the owner on
 * 15 Nov 2026 (see docs/11-BRANDING-AND-UX.md).
 */
object AppInfo {
    /** Chosen working name. Must never contain the name of an app this project is inspired by. */
    const val DISPLAY_NAME = "ClipHarbor"

    /** Reverse-DNS id of the app. Changing it makes the device treat the build as a different app. */
    const val APPLICATION_ID = "io.github.muzammilawan.clipharbor"

    /** This project is not affiliated with or endorsed by the yt-dlp maintainers. */
    const val IS_UNOFFICIAL = true

    /** Shown in the About screen and the diagnostic report header. */
    const val ENGINE_CREDIT = "Powered by yt-dlp (GPL-3.0)"

    /** Names that the project must never use as its own (AGENTS.md "Never"). */
    private val forbiddenBrands = listOf("snaptube", "seal", "ytdlnis", "youtube")

    /** True when [value] contains a brand this project is not allowed to reuse. Case-insensitive. */
    fun hasForbiddenBrand(value: String): Boolean {
        val lower = value.lowercase()
        return forbiddenBrands.any { lower.contains(it) }
    }
}
