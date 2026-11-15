package io.github.muzammilawan.clipharbor.engine

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundledEngineInstallerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bundle = "synthetic pinned engine".toByteArray()
    private val old = "previous engine".toByteArray()
    private val hash = sha256(bundle)

    private fun destination() = File(temporary.newFolder(), "yt-dlp")

    private fun installer(
        promote: (File, File) -> Unit = ::atomicMove,
        sync: (FileOutputStream) -> Unit = { it.fd.sync() },
    ) = BundledEngineInstaller(promote, sync)

    private fun install(
        destination: File,
        installer: BundledEngineInstaller = installer(),
        source: () -> InputStream = { bundle.inputStream() },
    ) = installer.install(destination, hash, bundle.size.toLong(), source)

    private fun assertOnlyDestination(destination: File) {
        assertEquals(listOf(destination.name), destination.parentFile!!.listFiles()!!.map { it.name })
        assertArrayEquals(old, destination.readBytes())
    }

    @Test fun cleanInstallCreatesDirectoryAndLeavesOnlyVerifiedEngine() {
        val destination = File(temporary.root, "new/engine/yt-dlp")
        val large = ByteArray(32 * 1024 + 37) { (it % 251).toByte() }
        assertEquals(BundleInstallResult.INSTALLED,
            installer().install(destination, sha256(large).uppercase(), large.size.toLong()) { large.inputStream() })
        assertArrayEquals(large, destination.readBytes())
        assertEquals(listOf("yt-dlp"), destination.parentFile!!.listFiles()!!.map { it.name })
    }

    @Test fun replacementStagesSyncsAndVerifiesBeforeTouchingOldFile() {
        val destination = destination().apply { writeBytes(old) }
        var synced = false
        var promotions = 0
        val installer = installer(
            promote = { staged, target ->
                assertTrue(synced)
                assertEquals(destination, target)
                assertEquals(destination.parentFile, staged.parentFile)
                assertNotEquals(target, staged)
                assertArrayEquals(old, target.readBytes())
                assertArrayEquals(bundle, staged.readBytes())
                promotions++
                atomicMove(staged, target)
            },
            sync = { it.fd.sync(); synced = true },
        )
        assertEquals(BundleInstallResult.INSTALLED, install(destination, installer))
        assertEquals(1, promotions)
        assertArrayEquals(bundle, destination.readBytes())
    }

    @Test fun matchingInstalledHashDoesNotOpenOrReplaceBundle() {
        val destination = destination().apply { writeBytes(bundle) }
        val installer = installer(promote = { _, _ -> fail("Unexpected replacement") })
        assertEquals(BundleInstallResult.ALREADY_INSTALLED, install(destination, installer) { error("Unexpected bundle open") })
        assertArrayEquals(bundle, destination.readBytes())
        assertEquals(1, destination.parentFile!!.listFiles()!!.size)
    }

    @Test fun sameLengthCorruptInstalledEngineIsReplaced() {
        val destination = destination().apply { writeBytes(ByteArray(bundle.size)) }
        assertEquals(BundleInstallResult.INSTALLED, install(destination))
        assertArrayEquals(bundle, destination.readBytes())
    }

    @Test fun corruptBundlePreservesOldEngineAndRemovesStaging() {
        val destination = destination().apply { writeBytes(old) }
        assertThrows(IOException::class.java) {
            install(destination, installer(promote = { _, _ -> fail("Unverified promotion") })) {
                ByteArray(bundle.size).inputStream()
            }
        }
        assertOnlyDestination(destination)
    }

    @Test fun corruptBundleOnCleanInstallLeavesNoEngineOrStaging() {
        val destination = destination()
        assertThrows(IOException::class.java) { install(destination) { ByteArray(bundle.size).inputStream() } }
        assertFalse(destination.exists())
        assertTrue(destination.parentFile!!.listFiles()!!.isEmpty())
    }

    @Test fun truncatedBundlePreservesOldEngine() {
        val destination = destination().apply { writeBytes(old) }
        assertThrows(IOException::class.java) { install(destination) { bundle.copyOf(bundle.size - 1).inputStream() } }
        assertOnlyDestination(destination)
    }

    @Test fun oversizedBundleIsBoundedBeforeSyncOrPromotion() {
        val destination = destination().apply { writeBytes(old) }
        val installer = installer(
            promote = { _, _ -> fail("Oversized promotion") },
            sync = { fail("Oversized sync") },
        )
        assertThrows(IOException::class.java) { install(destination, installer) { (bundle + byteArrayOf(1)).inputStream() } }
        assertOnlyDestination(destination)
    }

    @Test fun sourceOpenFailurePreservesOldEngine() {
        val destination = destination().apply { writeBytes(old) }
        assertThrows(IOException::class.java) { install(destination) { throw IOException("synthetic open failure") } }
        assertOnlyDestination(destination)
    }

    @Test fun partialCopyFailureClosesSourceAndPreservesOldEngine() {
        val destination = destination().apply { writeBytes(old) }
        var closed = false
        val source = object : InputStream() {
            var reads = 0
            override fun read(): Int = error("Bulk read expected")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw IOException("synthetic interrupted copy")
                bundle.copyInto(bytes, offset, 0, 3)
                return 3
            }
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { install(destination) { source } }
        assertTrue(closed)
        assertOnlyDestination(destination)
    }

    @Test fun sourceCloseFailureCannotPromoteEngine() {
        val destination = destination().apply { writeBytes(old) }
        val source = object : ByteArrayInputStream(bundle) {
            override fun close() { throw IOException("synthetic close failure") }
        }
        assertThrows(IOException::class.java) { install(destination) { source } }
        assertOnlyDestination(destination)
    }

    @Test fun syncFailurePreservesOldEngineAndCleansStaging() {
        val destination = destination().apply { writeBytes(old) }
        val installer = installer(
            promote = { _, _ -> fail("Unsynced promotion") },
            sync = { throw IOException("synthetic sync failure") },
        )
        assertThrows(IOException::class.java) { install(destination, installer) }
        assertOnlyDestination(destination)
    }

    @Test fun verificationReadsStagedFileNotJustSourceBytes() {
        val destination = destination().apply { writeBytes(old) }
        val installer = installer(
            promote = { _, _ -> fail("Corrupt staged promotion") },
            sync = { stream ->
                stream.fd.sync()
                destination.parentFile!!.listFiles()!!.single { it != destination }.writeBytes(ByteArray(bundle.size))
            },
        )
        assertThrows(IOException::class.java) { install(destination, installer) }
        assertOnlyDestination(destination)
    }

    @Test fun atomicReplacementFailurePreservesOldEngineAndCleansStaging() {
        val destination = destination().apply { writeBytes(old) }
        val installer = installer(promote = { _, _ -> throw IOException("synthetic replacement failure") })
        assertThrows(IOException::class.java) { install(destination, installer) }
        assertOnlyDestination(destination)
    }

    @Test fun failedAttemptCanRetryWithSameInstaller() {
        val destination = destination().apply { writeBytes(old) }
        val installer = installer()
        assertThrows(IOException::class.java) { install(destination, installer) { throw IOException("synthetic failure") } }
        assertOnlyDestination(destination)
        assertEquals(BundleInstallResult.INSTALLED, install(destination, installer))
        assertArrayEquals(bundle, destination.readBytes())
    }

    @Test fun orphanCleanupIsNarrowEvenOnMatchingEngineNoOp() {
        val destination = destination().apply { writeBytes(bundle) }
        val parent = destination.parentFile!!
        File(parent, "clipharbor-engine-stale.tmp").writeBytes(old)
        val unrelated = File(parent, "notes.tmp").apply { writeText("keep") }
        val otherSuffix = File(parent, "clipharbor-engine-keep.txt").apply { writeText("keep") }
        val directory = File(parent, "clipharbor-engine-directory.tmp").apply { mkdir() }
        val nested = File(directory, "clipharbor-engine-nested.tmp").apply { writeBytes(old) }
        val native = File(parent, "packages").apply { mkdir() }
        val python = File(native, "libpython.so").apply { writeBytes(old) }
        assertEquals(BundleInstallResult.ALREADY_INSTALLED, install(destination))
        assertEquals(setOf(destination, unrelated, otherSuffix, directory, native), parent.listFiles()!!.toSet())
        assertArrayEquals(old, nested.readBytes())
        assertArrayEquals(old, python.readBytes())
    }

    @Test fun preInterruptedThreadFailsWithoutOpeningBundleAndKeepsInterruptFlag() {
        val destination = destination().apply { writeBytes(old) }
        onWorker {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) { install(destination) { error("Unexpected open") } }
            assertTrue(Thread.currentThread().isInterrupted)
        }
        assertOnlyDestination(destination)
    }

    @Test fun interruptionAfterSyncCannotPromoteEngine() {
        val destination = destination().apply { writeBytes(old) }
        onWorker {
            val installer = installer(
                promote = { _, _ -> fail("Interrupted promotion") },
                sync = { it.fd.sync(); Thread.currentThread().interrupt() },
            )
            assertThrows(InterruptedIOException::class.java) { install(destination, installer) }
            assertTrue(Thread.currentThread().isInterrupted)
        }
        assertOnlyDestination(destination)
    }

    @Test fun concurrentInstallerInstancesSerializeAndSecondCallIsNoOp() {
        val destination = destination().apply { writeBytes(old) }
        val opened = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<BundleInstallResult> {
                install(destination, installer()) {
                    calls.incrementAndGet()
                    opened.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    bundle.inputStream()
                }
            }
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            val second = executor.submit<BundleInstallResult> {
                secondStarted.countDown()
                install(destination, installer()) { calls.incrementAndGet(); bundle.inputStream() }
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { second.get(100, TimeUnit.MILLISECONDS) }
            assertEquals(1, calls.get())
            assertArrayEquals(old, destination.readBytes())
            release.countDown()
            assertEquals(BundleInstallResult.INSTALLED, first.get(5, TimeUnit.SECONDS))
            assertEquals(BundleInstallResult.ALREADY_INSTALLED, second.get(5, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
            assertArrayEquals(bundle, destination.readBytes())
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun directoryDestinationIsNeverDeletedOrReplaced() {
        val destination = destination().apply { mkdir() }
        val child = File(destination, "keep").apply { writeBytes(old) }
        assertThrows(IOException::class.java) { install(destination) { error("Unexpected open") } }
        assertArrayEquals(old, child.readBytes())
        assertTrue(destination.isDirectory)
    }

    @Test fun nonDirectoryParentFailsWithoutOpeningBundle() {
        val parent = temporary.newFile().apply { writeBytes(old) }
        assertThrows(IOException::class.java) { install(File(parent, "yt-dlp")) { error("Unexpected open") } }
        assertArrayEquals(old, parent.readBytes())
    }

    @Test fun invalidPinsFailBeforeCreatingDirectoryOrOpeningSource() {
        val destination = File(temporary.root, "absent/yt-dlp")
        for ((digest, size) in listOf("invalid" to 1L, hash to 0L, hash to -1L)) {
            assertThrows(IllegalArgumentException::class.java) {
                installer().install(destination, digest, size) { error("Unexpected open") }
            }
        }
        assertFalse(destination.parentFile!!.exists())
    }

    private fun onWorker(block: () -> Unit) {
        val task = FutureTask<Unit> { try { block() } finally { Thread.interrupted() } }
        val worker = Thread(task)
        worker.start()
        try { task.get(5, TimeUnit.SECONDS) } finally { worker.interrupt(); worker.join(5_000) }
        assertFalse(worker.isAlive)
    }

    private fun atomicMove(staged: File, destination: File) {
        Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
