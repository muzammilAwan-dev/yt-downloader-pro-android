package io.github.muzammilawan.clipharbor.publish

/** A saved file shown in the Library tab. Own entries only; never a device-wide media query. */
data class LibraryItem(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val location: String,
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
}
