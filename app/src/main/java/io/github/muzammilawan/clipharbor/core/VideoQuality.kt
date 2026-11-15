package io.github.muzammilawan.clipharbor.core

/** Standard short-edge ceilings, matching yt-dlp's orientation-independent `res` concept. No upscaling. */
enum class VideoQuality(val pixels: Int, val label: String) {
    P144(144, "144p"), P240(240, "240p"), P360(360, "360p"), P480(480, "480p"),
    P720(720, "720p · HD"), P1080(1080, "1080p · Full HD"),
    P1440(1440, "1440p · QHD"), P2160(2160, "2160p · 4K"),
}

data class QualityChoice(val quality: VideoQuality, val width: Int? = null, val height: Int? = null) {
    val available: Boolean get() = width != null && height != null
    val detail: String get() = if (available) "Best reported: $width × $height · audio included"
        else "Unavailable in the reported formats"
}

/** App-owned ID filters. Keep the plan task-private; never construct it from share/clipboard command text. */
class VideoQualityPlan internal constructor(
    val quality: VideoQuality,
    internal val bestIds: List<String>,
    internal val cappedIds: List<String>,
) {
    override fun toString(): String = "VideoQualityPlan(quality=${quality.pixels})"
}

object VideoQualities {
    // Only known dimensions and safely representable IDs can substantiate a quality promise.
    private val safeId = Regex("[A-Za-z0-9_.-]{1,128}")
    private fun eligible(formats: List<FormatInfo>): List<FormatInfo> {
        val separateAudio = formats.any { it.hasAudio && !it.hasVideo }
        val uniqueIds = formats.groupingBy { it.formatId }.eachCount().filterValues { it == 1 }.keys
        return formats.filter { it.hasVideo && (it.hasAudio || separateAudio) &&
            it.width?.let { w -> w > 0 } == true && it.height?.let { h -> h > 0 } == true &&
            safeId.matches(it.formatId) && it.formatId in uniqueIds }
            .takeIf { it.size <= 512 }.orEmpty()
    }

    fun resolution(format: FormatInfo): Int? =
        if ((format.width ?: 0) > 0 && (format.height ?: 0) > 0) minOf(format.width!!, format.height!!) else null

    fun choices(formats: List<FormatInfo>): List<QualityChoice> {
        val video = eligible(formats)
        return VideoQuality.entries.mapIndexed { index, quality ->
            val previous = VideoQuality.entries.getOrNull(index - 1)?.pixels ?: 0
            // Do not offer eight aliases for the same low-quality stream. Missing tiers remain visible but disabled.
            val best = video.filter { resolution(it)!! in (previous + 1)..quality.pixels }
                .maxByOrNull { resolution(it)!! }
            QualityChoice(quality, best?.width, best?.height)
        }.reversed()
    }

    fun default(choices: List<QualityChoice>): VideoQuality? =
        choices.filter { it.available && it.quality.pixels <= 1080 }.maxByOrNull { it.quality.pixels }?.quality
            ?: choices.filter { it.available }.minByOrNull { it.quality.pixels }?.quality

    fun plan(formats: List<FormatInfo>, quality: VideoQuality): VideoQualityPlan? {
        if (choices(formats).none { it.quality == quality && it.available }) return null
        val capped = eligible(formats).filter { resolution(it)!! <= quality.pixels }
        val best = capped.maxOf { resolution(it)!! }
        return VideoQualityPlan(quality,
            capped.filter { resolution(it) == best }.map { it.formatId }.distinct().sorted(),
            capped.map { it.formatId }.distinct().sorted())
    }
}
