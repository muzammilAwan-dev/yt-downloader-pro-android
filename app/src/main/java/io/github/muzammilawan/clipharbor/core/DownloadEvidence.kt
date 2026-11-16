package io.github.muzammilawan.clipharbor.core

import java.io.File

/** Parses only explicit engine evidence; never guesses an output from timestamps or directory contents. */
class DownloadEvidence {
    private var finalPath: String? = null
    private var selectedIds: List<String> = emptyList()
    private var selectedHeight: Int? = null
    private var selectedWidth: Int? = null
    private var selectedAudioId: String? = null

    @Synchronized
    fun accept(line: String) {
        val text = line.removeSuffix("\r")
        when {
            text.startsWith(FILE_MARKER) -> finalPath = text.removePrefix(FILE_MARKER)
            text.startsWith(AUDIO_MARKER) -> {
                selectedAudioId = null
                val fields = text.removePrefix(AUDIO_MARKER).split('\t')
                if (fields.size == 3 && fields[1] == "none" && fields[2] != "none" &&
                    Regex("[A-Za-z0-9_.-]{1,128}").matches(fields[2])) {
                    selectedAudioId = parseIds(fields[0])?.singleOrNull()
                }
            }
            text.startsWith(SELECTION_MARKER) -> {
                val fields = text.removePrefix(SELECTION_MARKER).split('\t')
                if (fields.size !in 2..3) return
                val ids = parseIds(fields[0]) ?: return
                if (fields.size == 3 && fields.drop(1).any { it != "NA" && it.toIntOrNull()?.let { n -> n > 0 } != true }) return
                selectedIds = ids
                selectedAudioId = null
                selectedWidth = if (fields.size == 3) fields[1].toIntOrNull()?.takeIf { it > 0 } else null
                selectedHeight = fields.last().toIntOrNull()?.takeIf { it > 0 }
            }
            else -> {
                val match = INFO_SELECTION.matchEntire(text) ?: return
                selectedIds = parseIds(match.groupValues[1]) ?: return
                selectedAudioId = null
                // A later selection can be a fallback. Do not keep the previous selection's height.
                selectedHeight = null
                selectedWidth = null
            }
        }
    }

    /** Only a nonempty final media file inside this task's canonical staging directory is publishable. */
    @Synchronized
    fun outputFiles(directory: File): List<File> {
        val path = finalPath ?: return emptyList()
        val file = File(path)
        if (!file.isAbsolute) return emptyList()
        val root = directory.canonicalFile
        val canonical = file.canonicalFile
        // Walk canonical parents: path-segment-safe and works on API 24 without java.nio desugaring.
        if (generateSequence(canonical.parentFile) { it.parentFile }.none { it == root }) return emptyList()
        if (!canonical.isFile || canonical.length() <= 0 || canonical.extension.lowercase() !in MEDIA_EXTENSIONS) {
            return emptyList()
        }
        return listOf(canonical)
    }

    @Synchronized
    fun formatIds(): List<String> = selectedIds.toList()

    @Synchronized
    fun height(): Int? = selectedHeight

    @Synchronized
    fun width(): Int? = selectedWidth

    @Synchronized
    fun audioFormatId(): String? = selectedAudioId

    private fun parseIds(value: String): List<String>? =
        value.takeIf { it.length <= 256 && FORMAT_IDS.matches(it) }?.split('+')

    companion object {
        const val FILE_MARKER = "CLIPHARBOR_FILE:"
        const val SELECTION_MARKER = "CLIPHARBOR_SELECTED:"
        const val FILE_PRINT = "after_move:${FILE_MARKER}%(filepath)s"
        const val SELECTION_PRINT = "before_dl:${SELECTION_MARKER}%(format_id)s\t%(width)s\t%(height)s"
        const val AUDIO_MARKER = "CLIPHARBOR_AUDIO:"
        const val AUDIO_PRINT = "before_dl:${AUDIO_MARKER}%(format_id)s\t%(vcodec)s\t%(acodec)s"

        private val INFO_SELECTION = Regex("^\\[info]\\s+\\S+: Downloading \\d+ format\\(s\\): (\\S+)\\s*$")
        private val FORMAT_IDS = Regex("[A-Za-z0-9_.-]+(?:\\+[A-Za-z0-9_.-]+)*")
        private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "ogg", "flac", "wav")
    }
}
