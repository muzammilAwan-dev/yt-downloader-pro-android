package io.github.muzammilawan.clipharbor.engine

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

internal enum class BundleInstallResult { INSTALLED, ALREADY_INSTALLED }

/**
 * Installs only bytes supplied by the APK; no network or engine execution.
 *
 * The caller supplies an app-owned destination and a same-directory atomic replacement (Android: Os.rename).
 * Replacement must either succeed atomically or throw without changing the destination; never delete it first.
 * This lock serializes installers, not engine tasks: the adapter must install once before any engine execution.
 */
internal class BundledEngineInstaller(
    private val replaceAtomically: (File, File) -> Unit,
    private val sync: (FileOutputStream) -> Unit = { it.fd.sync() },
) {
    fun install(
        destination: File,
        expectedSha256: String,
        expectedBytes: Long,
        openBundle: () -> InputStream,
    ): BundleInstallResult {
        require(expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid bundled engine hash" }
        require(expectedBytes > 0) { "Invalid bundled engine size" }
        return synchronized(installLock) {
            checkInterrupted()
            val parent = destination.absoluteFile.parentFile ?: throw IOException("Missing engine directory")
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create engine directory")
            if (destination.exists() && !destination.isFile) throw IOException("Engine destination is not a file")
            cleanStaging(parent)
            if (matches(destination, expectedSha256, expectedBytes)) {
                return@synchronized BundleInstallResult.ALREADY_INSTALLED
            }

            val staged = File.createTempFile(PREFIX, SUFFIX, parent)
            var failure: Throwable? = null
            try {
                FileOutputStream(staged).use { output ->
                    openBundle().use { input -> copyBounded(input, output, expectedBytes) }
                    output.flush()
                    checkInterrupted()
                    sync(output)
                }
                if (!matches(staged, expectedSha256, expectedBytes)) throw IOException("Bundled engine verification failed")
                checkInterrupted()
                replaceAtomically(staged, destination)
                BundleInstallResult.INSTALLED
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                if (staged.exists() && !staged.delete()) {
                    val cleanup = IOException("Cannot remove staged engine")
                    if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun copyBounded(input: InputStream, output: FileOutputStream, expectedBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            checkInterrupted()
            val read = input.read(buffer)
            if (read < 0) break
            if (read.toLong() > expectedBytes - copied) throw IOException("Bundled engine size mismatch")
            output.write(buffer, 0, read)
            copied += read
        }
        if (copied != expectedBytes) throw IOException("Bundled engine size mismatch")
    }

    private fun matches(file: File, expectedSha256: String, expectedBytes: Long): Boolean {
        if (!file.isFile || file.length() != expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        file.inputStream().use { input ->
            while (true) {
                checkInterrupted()
                val read = input.read(buffer)
                if (read < 0) break
                if (read.toLong() > expectedBytes - total) return false
                digest.update(buffer, 0, read)
                total += read
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return total == expectedBytes && actual.equals(expectedSha256, ignoreCase = true)
    }

    /** Interrupted-process remnants only; never traverses directories or clears the native runtime. */
    private fun cleanStaging(parent: File) {
        val files = parent.listFiles() ?: throw IOException("Cannot inspect engine directory")
        files.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }.forEach {
            if (!it.delete()) throw IOException("Cannot remove staged engine")
        }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Bundled engine installation interrupted")
    }

    private companion object {
        val installLock = Any()
        const val PREFIX = "clipharbor-engine-"
        const val SUFFIX = ".tmp"
    }
}
