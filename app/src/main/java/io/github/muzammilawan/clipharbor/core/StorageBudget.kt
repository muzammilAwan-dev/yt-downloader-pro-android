package io.github.muzammilawan.clipharbor.core

import java.util.Locale

data class StorageSpace(val privateBytes: Long, val destinationBytes: Long)
data class StorageReport(val allowed: Boolean, val availableBytes: Long, val requiredBytes: Long, val estimatedBytes: Long?) {
    val summary: String get() = "Free: ${mb(availableBytes)} MB · safety budget: ${mb(requiredBytes)} MB. " +
        if (estimatedBytes == null) "Media size unknown; this check cannot guarantee enough space."
        else "Reported sizes are estimates, not guaranteed final sizes."
    private fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes.coerceAtLeast(0) / 1_048_576.0)
}

/** Conservative shared-volume bound: parts + merged file + publication copy, plus DB/log headroom. */
object StorageBudget {
    const val RESERVE = 64L * 1024 * 1024
    fun download(space: StorageSpace, estimate: Long?, merging: Boolean): StorageReport {
        val known = estimate?.takeIf { it > 0 }
        val budget = plus(RESERVE, times(known ?: 0, if (merging) 3 else 2))
        val free = minOf(space.privateBytes, space.destinationBytes).coerceAtLeast(0)
        return StorageReport(free >= budget, free, budget, known)
    }
    fun publication(space: StorageSpace, bytes: Long): StorageReport {
        val free = space.destinationBytes.coerceAtLeast(0)
        val budget = plus(RESERVE, bytes.coerceAtLeast(0))
        return StorageReport(bytes > 0 && free >= budget, free, budget, bytes)
    }

    /** Maximum reported candidate, not a claim about which fallback yt-dlp will select. */
    fun estimate(formats: List<FormatInfo>, plan: VideoQualityPlan): Long? {
        val videos = formats.filter { it.formatId in plan.cappedIds }
        val audios = formats.filter { it.hasAudio && !it.hasVideo }
        if (videos.isEmpty() || videos.any { (it.approxBytes ?: 0) <= 0 }) return null
        if (videos.any { !it.hasAudio } && (audios.isEmpty() || audios.any { (it.approxBytes ?: 0) <= 0 })) return null
        val video = videos.maxOf { it.approxBytes!! }
        return plus(video, if (videos.any { !it.hasAudio }) audios.maxOf { it.approxBytes!! } else 0)
    }
    private fun plus(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
    private fun times(a: Long, b: Int): Long = if (a > Long.MAX_VALUE / b) Long.MAX_VALUE else a * b
}
