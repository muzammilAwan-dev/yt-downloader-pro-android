package io.github.muzammilawan.clipharbor.publish

/** A saved file shown in the Library tab. Own entries only; never a device-wide media query. */
data class LibraryItem(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val location: String,
    val durationMs: Long? = null,
) {
    override fun toString(): String = "LibraryItem(name=$name, kind=${LibraryCatalog.kindOf(mimeType)})"
}

enum class LibraryKind { VIDEO, AUDIO, IMAGE, OTHER }

/** Pure filtering/sorting so the Library rules are testable without Android or MediaStore. */
object LibraryCatalog {
    fun kindOf(mimeType: String): LibraryKind = when {
        mimeType.startsWith("video/", ignoreCase = true) -> LibraryKind.VIDEO
        mimeType.startsWith("audio/", ignoreCase = true) -> LibraryKind.AUDIO
        mimeType.startsWith("image/", ignoreCase = true) -> LibraryKind.IMAGE
        else -> LibraryKind.OTHER
    }

    /**
     * Newest first. A blank query means "everything"; a kind of null means "all kinds".
     * Matching is a case-insensitive substring on the display name only.
     */
    fun visible(items: List<LibraryItem>, query: String, kind: LibraryKind?): List<LibraryItem> {
        val needle = query.trim()
        return items.asSequence()
            .filter { kind == null || kindOf(it.mimeType) == kind }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .sortedWith(compareByDescending<LibraryItem> { it.addedAt }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun totalBytes(items: List<LibraryItem>): Long = items.sumOf { it.sizeBytes.coerceAtLeast(0) }

    /** Newest [limit] entries for the Home preview; never a second sort order. */
    fun recent(items: List<LibraryItem>, limit: Int): List<LibraryItem> =
        if (limit <= 0) emptyList() else visible(items, "", null).take(limit)

    /** Human-readable base-1024 size; negative sizes read as zero rather than as a negative file. */
    fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0)
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = -1
        while (unit < units.lastIndex && size >= 1023.95) {
            size /= 1024.0
            unit++
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", size, units[unit])
    }

    /** `m:ss` or `h:mm:ss`; unknown or non-positive durations are omitted rather than guessed. */
    fun formatDuration(durationMs: Long?): String? {
        val total = durationMs ?: return null
        if (total <= 0) return null
        val seconds = total / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds % 60)
    }
}
