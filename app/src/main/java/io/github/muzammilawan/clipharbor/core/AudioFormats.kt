package io.github.muzammilawan.clipharbor.core

import java.util.Locale

data class AudioChoice(val formatId: String, val extension: String, val codec: String, val approxBytes: Long?) {
    val label: String get() {
        val audio = when {
            codec.startsWith("mp4a") || codec == "aac" -> "AAC"
            codec == "opus" -> "Opus"
            codec == "mp3" -> "MP3"
            codec == "vorbis" -> "Vorbis"
            codec == "flac" -> "FLAC"
            codec.startsWith("pcm_") -> "PCM"
            else -> "audio"
        }
        val container = if (extension == "webm") "WebM" else extension.uppercase(Locale.ROOT)
        return "$container · $audio"
    }
    val detail: String get() = "Original audio · format $formatId" +
        (approxBytes?.takeIf { it > 0 }?.let { " · ≈ ${String.format(Locale.ROOT, "%.1f", it / 1_000_000.0)} MB" } ?: "")
    override fun toString(): String = "AudioChoice"
}

/** App-owned, task-private selection; no share/clipboard option can create this plan. */
class AudioPlan internal constructor(internal val formatId: String, val extension: String, val mimeType: String) {
    override fun toString(): String = "AudioPlan"
}

object AudioFormats {
    private val safeId = Regex("[A-Za-z0-9_.-]{1,128}")
    private val mimeTypes = mapOf("m4a" to "audio/mp4", "webm" to "audio/webm", "mp3" to "audio/mpeg",
        "opus" to "audio/ogg", "ogg" to "audio/ogg", "flac" to "audio/flac", "wav" to "audio/wav")

    fun choices(formats: List<FormatInfo>): List<AudioChoice> {
        val uniqueIds = formats.groupingBy { it.formatId }.eachCount().filterValues { it == 1 }.keys
        val eligible = formats.filter { it.vcodec == "none" && it.hasAudio && it.ext in mimeTypes &&
            safeId.matches(it.formatId) && it.formatId in uniqueIds }
        if (eligible.size > 512) return emptyList()
        return eligible.map { AudioChoice(it.formatId, it.ext!!, it.acodec!!, it.approxBytes) }
            .sortedWith(compareBy<AudioChoice> { if (it.extension == "m4a") 0 else 1 }.thenBy { it.extension }.thenBy { it.formatId })
    }

    fun plan(formats: List<FormatInfo>, formatId: String): AudioPlan? =
        choices(formats).singleOrNull { it.formatId == formatId }?.let { AudioPlan(it.formatId, it.extension, mimeTypes.getValue(it.extension)) }

    /** Exact audio-only selection with no fallback to another ID, muxed video or conversion. */
    fun selector(plan: AudioPlan): String {
        require(safeId.matches(plan.formatId) && mimeTypes[plan.extension] == plan.mimeType)
        return "ba[format_id='${plan.formatId}']"
    }

    fun outputTemplate(): String = "%(title).150B [%(id)s]_audio.%(ext)s"
}
