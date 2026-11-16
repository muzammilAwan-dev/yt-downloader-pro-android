package io.github.muzammilawan.clipharbor.publish

import java.io.File
import java.util.UUID

/** Platform-free commit/collision boundaries; adapters supply MediaStore or narrow app-specific storage. */
object PublicationFiles {
    fun <T : Any> pending(insert: () -> T?, copy: (T) -> Unit, commit: (T) -> Int, discard: (T) -> Unit): T {
        val uri = checkNotNull(insert()) { "Publication refused the insert" }
        try {
            copy(uri)
            check(commit(uri) == 1) { "Publication did not commit exactly one row" }
            return uri
        } catch (error: Exception) {
            runCatching { discard(uri) }
            throw error
        }
    }

    fun legacy(source: File, destination: File): File {
        require(source.isFile && source.length() > 0) { "Publication source is missing or empty" }
        check(destination.isDirectory || destination.mkdirs()) { "Cannot create publication directory" }
        var target = File(destination, source.name)
        if (!target.createNewFile()) {
            target = File(destination, "${source.nameWithoutExtension.take(150)}-${UUID.randomUUID()}.${source.extension}")
            check(target.createNewFile()) { "Cannot reserve unique publication file" }
        }
        try {
            source.inputStream().use { input -> target.outputStream().use { output ->
                check(input.copyTo(output) == source.length()) { "Publication copy is incomplete" }
                output.flush()
            } }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
}
