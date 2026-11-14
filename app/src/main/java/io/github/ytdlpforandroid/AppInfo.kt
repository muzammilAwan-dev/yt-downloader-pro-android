package io.github.ytdlpforandroid

/**
 * Static, platform-free app metadata.
 *
 * Kept free of Android imports so it can be unit-tested on the JVM. The display name is a working name and will change
 * when the branding decision is made (see docs/11-BRANDING-AND-UX.md).
 */
object AppInfo {
    /** Working name. Must never contain the name of an app this project is inspired by. */
    const val DISPLAY_NAME = "yt-dlp for Android"

    /** This project is not affiliated with or endorsed by the yt-dlp maintainers. */
    const val IS_UNOFFICIAL = true

    /** Names that the project must never use as its own (AGENTS.md "Never"). */
    private val forbiddenBrands = listOf("snaptube", "seal", "ytdlnis", "youtube")

    /** True when [value] contains a brand this project is not allowed to reuse. Case-insensitive. */
    fun hasForbiddenBrand(value: String): Boolean {
        val lower = value.lowercase()
        return forbiddenBrands.any { lower.contains(it) }
    }
}
