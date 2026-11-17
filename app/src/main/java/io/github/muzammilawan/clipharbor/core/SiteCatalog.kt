package io.github.muzammilawan.clipharbor.core

/**
 * One supported source, mirroring `contracts/sites.json`.
 *
 * Android support is UNKNOWN for every site until it is tested on a phone, so the UI must not imply otherwise.
 * Only the public home page is opened; no in-app browser, login or cookie handling exists in Phase 2A.
 */
data class SiteShortcut(
    val id: String,
    val label: String,
    val homeUrl: String,
    val loginHint: Boolean,
)

object SiteCatalog {
    /** Order matches `contracts/sites.json`; [SiteCatalogTest] keeps the two in sync. */
    val sites: List<SiteShortcut> = listOf(
        SiteShortcut("youtube", "YouTube", "https://www.youtube.com", false),
        SiteShortcut("instagram", "Instagram", "https://www.instagram.com", true),
        SiteShortcut("facebook", "Facebook", "https://www.facebook.com", true),
        SiteShortcut("x", "X (Twitter)", "https://x.com", false),
        SiteShortcut("tiktok", "TikTok", "https://www.tiktok.com", false),
        SiteShortcut("reddit", "Reddit", "https://www.reddit.com", false),
        SiteShortcut("twitch", "Twitch", "https://www.twitch.tv", false),
        SiteShortcut("vimeo", "Vimeo", "https://vimeo.com", true),
        SiteShortcut("soundcloud", "SoundCloud", "https://soundcloud.com", false),
    )
}
