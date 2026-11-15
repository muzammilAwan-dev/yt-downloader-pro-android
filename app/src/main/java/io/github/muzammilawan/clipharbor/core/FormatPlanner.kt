package io.github.muzammilawan.clipharbor.core

/**
 * Turns a resolved format list into (a) a deterministic yt-dlp format selector and (b) a one-line summary for the
 * diagnostic log. Pure Kotlin so the Phase 0 spike logic is unit-tested without a device.
 */
object FormatPlanner {
    /**
     * Prefers H.264 video + AAC audio (best chance of a device-playable MP4 after merging), capped at [maxHeight],
     * then falls back to any codecs, then to a single muxed stream.
     *
     * Never fall back above the requested ceiling; missing compatible formats must fail visibly instead.
     */
    fun selector(maxHeight: Int): String {
        require(maxHeight > 0) { "maxHeight must be positive" }
        return "bv*[height<=$maxHeight][vcodec^=avc1]+ba[acodec^=mp4a]/" +
            "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]"
    }

    /** Resolution first, compatibility within that resolution, then strictly capped lower fallbacks. */
    fun selector(plan: VideoQualityPlan): String {
        fun ids(values: List<String>): String {
            require(values.isNotEmpty() && values.size <= 512)
            require(values.all { it.matches(Regex("[A-Za-z0-9_.-]{1,128}")) })
            // Python-compatible regex escaping, not Kotlin Regex.escape (which emits Java-only \Q…\E).
            return "[format_id~='^(?:${values.joinToString("|") { it.replace(".", "\\.") }})$']"
        }
        val best = ids(plan.bestIds)
        val cap = ids(plan.cappedIds)
        return "bv*$best[vcodec^=avc1]+ba[acodec^=mp4a]/" +
            "b$best[vcodec^=avc1][acodec^=mp4a]/bv*$best+ba/b$best/bv*$cap+ba/b$cap"
    }

    /** A ceiling is labelled `up-to`, never falsely presented as the actual encoded resolution. */
    fun outputTemplate(quality: VideoQuality): String =
        "%(title).150B [%(id)s]_up-to-${quality.pixels}p.%(ext)s"

    /**
     * Output name inside the app-private working directory. `%(title).150B` truncates by bytes so long titles cannot
     * exceed filesystem limits; `--windows-filenames` (added by the engine) removes characters Android cannot store.
     */
    fun outputTemplate(): String = "%(title).150B [%(id)s].%(ext)s"

    fun summary(formats: List<FormatInfo>, targetHeight: Int = 1080): FormatSummary =
        FormatSummary.of(formats, targetHeight)

    /** `formats=26 videoOnly=12 audioOnly=4 muxed=10 maxHeight=1080 hdWithheld=false` */
    fun describe(summary: FormatSummary): String = buildString {
        append("formats=").append(summary.total)
        append(" videoOnly=").append(summary.videoOnly)
        append(" audioOnly=").append(summary.audioOnly)
        append(" muxed=").append(summary.muxed)
        append(" maxHeight=").append(summary.maxHeight ?: "none")
        append(" maxHeightMuxed=").append(summary.maxHeightMuxed ?: "none")
        append(" hdUnavailable=").append(summary.hdUnavailable)
    }

    /** True when the resolve result cannot satisfy a 1080p request; the UI should say so before downloading. */
    fun hdRequestWillFallShort(summary: FormatSummary, requestedHeight: Int): Boolean =
        summary.total > 0 && (summary.maxHeight ?: 0) < requestedHeight
}
