package io.github.muzammilawan.clipharbor.core

import java.util.Locale

data class ExactVideoChoice(val formatId: String, val width: Int, val height: Int, val extension: String,
    val videoCodec: String, val fps: Int?, val muxed: Boolean, val approxBytes: Long?) {
    val label: String get() = "$width × $height · ${extension.uppercase(Locale.ROOT)}"
    val detail: String get() = "Format $formatId · $videoCodec" + (fps?.let { " · $it fps" } ?: "") +
        if (muxed) " · source audio included" else " · audio selected automatically"
    override fun toString(): String = "ExactVideoChoice"
}

/** Task-private exact source plan: exact video ID, bounded reported audio candidates, no different-video fallback. */
class ExactVideoPlan internal constructor(val choice: ExactVideoChoice, internal val audioIds: List<String>,
    val estimatedBytes: Long?) {
    val mergeContainer: String get() = "mkv" // Explicit advanced mode avoids codec/container compatibility guesses.
    override fun toString(): String = "ExactVideoPlan"
}

object ExactVideoFormats {
    private val safeId = Regex("[A-Za-z0-9_.-]{1,128}")
    fun choices(formats: List<FormatInfo>): List<ExactVideoChoice> {
        val unique = formats.groupingBy { it.formatId }.eachCount().filterValues { it == 1 }.keys
        val audio = AudioFormats.choices(formats).isNotEmpty()
        val video = formats.filter { it.hasVideo && (it.hasAudio || it.acodec == "none" && audio) &&
            (it.width ?: 0) > 0 && (it.height ?: 0) > 0 && it.ext in setOf("mp4", "webm", "mkv") &&
            safeId.matches(it.formatId) && it.formatId in unique && safeId.matches(it.vcodec!!) }
        if (video.size > 512) return emptyList()
        return video.map { ExactVideoChoice(it.formatId, it.width!!, it.height!!, it.ext!!, it.vcodec!!,
            it.fps?.takeIf { fps -> fps > 0 }, it.hasAudio, it.approxBytes?.takeIf { size -> size > 0 }) }
            .sortedWith(compareByDescending<ExactVideoChoice> { minOf(it.width, it.height) }
                .thenByDescending { it.fps ?: 0 }.thenBy { it.formatId })
    }
    fun plan(formats: List<FormatInfo>, id: String): ExactVideoPlan? {
        val choice = choices(formats).singleOrNull { it.formatId == id } ?: return null
        val audio = if (choice.muxed) emptyList() else AudioFormats.choices(formats)
        val size = choice.approxBytes?.let { video ->
            if (choice.muxed) video else if (audio.any { (it.approxBytes ?: 0) <= 0 }) null
            else audio.maxOf { it.approxBytes!! }.let { bytes -> if (Long.MAX_VALUE - video < bytes) Long.MAX_VALUE else video + bytes }
        }
        return ExactVideoPlan(choice, audio.map { it.formatId }, size)
    }
    fun selector(plan: ExactVideoPlan): String {
        require(safeId.matches(plan.choice.formatId))
        if (plan.choice.muxed) return "b[format_id='${plan.choice.formatId}']"
        require(plan.audioIds.isNotEmpty() && plan.audioIds.size <= 512 && plan.audioIds.all(safeId::matches))
        val video = "bv[format_id='${plan.choice.formatId}']"
        val audio = "ba[format_id~='^(?:${plan.audioIds.joinToString("|") { it.replace(".", "\\.") }})$']"
        return "$video+$audio[acodec^=mp4a]/$video+$audio"
    }
    fun matches(plan: ExactVideoPlan, ids: List<String>, width: Int?, height: Int?): Boolean =
        width == plan.choice.width && height == plan.choice.height &&
            if (plan.choice.muxed) ids == listOf(plan.choice.formatId)
            else ids.size == 2 && ids[0] == plan.choice.formatId && ids[1] in plan.audioIds

    fun outputTemplate(plan: ExactVideoPlan): String = "%(title).150B [%(id)s]_format-${plan.choice.formatId}.%(ext)s"
}
