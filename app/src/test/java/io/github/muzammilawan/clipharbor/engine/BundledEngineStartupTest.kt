package io.github.muzammilawan.clipharbor.engine

import io.github.muzammilawan.clipharbor.core.EngineInfo
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline startup ordering/failure tests; native Android initialization is still a device check. */
class BundledEngineStartupTest {
    @get:Rule val temporary = TemporaryFolder()
    private val module = sequenceOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val asset = File(module, "src/main/assets/${BundledEngineStartup.ASSET}")
    private val pin = EngineInfo(ytdlpVersion = "2026.10.26", ejsVersion = "0.8.0")

    @Test fun installationCompletesBeforeLibrariesAndReportsVerifiedPin() {
        val events = mutableListOf<String>()
        val result = BundledEngineStartup().initialize(
            install = { events += "verified installation" },
            initializeLibraries = { events += "native init" },
        )
        assertEquals(listOf("verified installation", "native init"), events)
        assertEquals(pin, result)
    }

    @Test fun repeatedAndSecondAdapterInitNeverReinstallOrReinitialize() {
        val startup = BundledEngineStartup()
        val first = startup.initialize({}, {})
        val second = startup.initialize({ fail("Second adapter must not install") }, { fail("Must not reinitialize") })
        assertSame(first, second)
        assertEquals(pin, second)
    }

    @Test fun installationFailureBlocksLibrariesAndDoesNotReturnSuccess() {
        val failure = IOException("synthetic verification failure")
        val actual = assertThrows(IOException::class.java) {
            BundledEngineStartup().initialize({ throw failure }, { fail("Unverified engine must not initialize") })
        }
        assertSame(failure, actual)
    }

    @Test fun failedInstallationCanRetryRatherThanCacheFailureOrOldVersion() {
        val startup = BundledEngineStartup()
        assertThrows(IOException::class.java) { startup.initialize({ throw IOException("synthetic failure") }, {}) }
        val events = mutableListOf<String>()
        assertEquals(pin, startup.initialize({ events += "install" }, { events += "libraries" }))
        assertEquals(listOf("install", "libraries"), events)
    }

    @Test fun libraryFailureDoesNotMarkStartupReadyAndRetryVerifiesAgain() {
        val startup = BundledEngineStartup()
        val installations = AtomicInteger()
        val libraries = AtomicInteger()
        assertThrows(IOException::class.java) {
            startup.initialize({ installations.incrementAndGet() }, {
                libraries.incrementAndGet()
                throw IOException("synthetic native failure")
            })
        }
        assertEquals(pin, startup.initialize({ installations.incrementAndGet() }, { libraries.incrementAndGet() }))
        assertEquals(2, installations.get())
        assertEquals(2, libraries.get())
    }

    @Test fun cancellationIsPropagatedAndDoesNotMarkReady() {
        val startup = BundledEngineStartup()
        val canceled = CancellationException("synthetic cancellation")
        assertSame(canceled, assertThrows(CancellationException::class.java) {
            startup.initialize({ throw canceled }, { fail("Canceled init") })
        })
        assertEquals(pin, startup.initialize({}, {}))
    }

    @Test fun concurrentAdaptersWaitForInstallationAndShareOneSuccessfulInit() {
        concurrentInit(blockInLibraries = false)
    }

    @Test fun concurrentAdaptersAlsoWaitUntilNativeLibrariesFinish() {
        concurrentInit(blockInLibraries = true)
    }

    private fun concurrentInit(blockInLibraries: Boolean) {
        val startup = BundledEngineStartup()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val installations = AtomicInteger()
        val libraries = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<EngineInfo> {
                startup.initialize({
                    installations.incrementAndGet()
                    if (!blockInLibraries) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                }, {
                    libraries.incrementAndGet()
                    if (blockInLibraries) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
                })
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = pool.submit<EngineInfo> {
                secondStarted.countDown()
                startup.initialize({ fail("Concurrent reinstall") }, { fail("Concurrent library init") })
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { second.get(100, TimeUnit.MILLISECONDS) }
            assertEquals(1, installations.get())
            assertEquals(if (blockInLibraries) 1 else 0, libraries.get())
            release.countDown()
            val result = first.get(5, TimeUnit.SECONDS)
            assertEquals(pin, result)
            assertSame(result, second.get(5, TimeUnit.SECONDS))
            assertEquals(1, libraries.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun waitingAdapterCanRetryAfterFirstInstallationFails() {
        val startup = BundledEngineStartup()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<EngineInfo> {
                startup.initialize({
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    throw IOException("synthetic first attempt failure")
                }, { fail("First init must not reach libraries") })
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = pool.submit<EngineInfo> {
                secondStarted.countDown()
                startup.initialize({}, {})
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { second.get(100, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertTrue(assertThrows(ExecutionException::class.java) { first.get(5, TimeUnit.SECONDS) }.cause is IOException)
            assertEquals(pin, second.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun activityRecreationDuringFakeWorkerCannotReplaceRunningEngine() {
        val startup = BundledEngineStartup()
        val destination = File(temporary.newFolder(), "yt-dlp")
        startup.initialize({ install(destination) }, {})
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = FutureTask<Unit> {
            assertEquals(BundledEngineStartup.SHA256, sha256(destination.readBytes()))
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            assertEquals(BundledEngineStartup.SHA256, sha256(destination.readBytes()))
        }
        val thread = Thread(worker)
        thread.start()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(pin, startup.initialize({ fail("Replacement during active worker") }, { fail("Reinitialization") }))
            release.countDown()
            worker.get(5, TimeUnit.SECONDS)
        } finally { release.countDown(); thread.interrupt(); thread.join(5_000) }
        assertFalse(thread.isAlive)
    }

    @Test fun productionPinInstallsActualAssetBeforeCleanNativeInitialization() {
        val destination = File(temporary.root, "new/engine/yt-dlp")
        assertEquals(pin, BundledEngineStartup().initialize({ install(destination) }, {
            assertEquals(BundledEngineStartup.BYTES, destination.length())
            assertEquals(BundledEngineStartup.SHA256, sha256(destination.readBytes()))
        }))
    }

    @Test fun freshProcessRechecksPinWithoutOpeningAlreadyMatchingAsset() {
        val destination = File(temporary.newFolder(), "yt-dlp")
        BundledEngineStartup().initialize({ install(destination) }, {})
        var libraries = 0
        assertEquals(pin, BundledEngineStartup().initialize({
            assertEquals(BundleInstallResult.ALREADY_INSTALLED, installer().install(
                destination, BundledEngineStartup.SHA256, BundledEngineStartup.BYTES,
            ) { error("Matching engine must not reopen asset") })
        }, { libraries++ }))
        assertEquals(1, libraries)
    }

    @Test fun corruptAssetPreservesOldEngineBlocksLibrariesAndCanRetry() {
        val destination = File(temporary.newFolder(), "yt-dlp").apply { writeText("synthetic previous engine") }
        val startup = BundledEngineStartup()
        assertThrows(IOException::class.java) {
            startup.initialize({
                installer().install(destination, BundledEngineStartup.SHA256, BundledEngineStartup.BYTES) {
                    ByteArray(BundledEngineStartup.BYTES.toInt()).inputStream()
                }
            }, { fail("Must not fall back to old engine") })
        }
        assertEquals("synthetic previous engine", destination.readText())
        assertEquals(listOf(destination), destination.parentFile!!.listFiles()!!.toList())
        assertEquals(pin, startup.initialize({ install(destination) }, {}))
    }

    @Test fun interruptedBeforeStartupCannotInstallOrInitialize() {
        onWorker {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) {
                BundledEngineStartup().initialize({ fail("Interrupted install") }, { fail("Interrupted libraries") })
            }
            assertTrue(Thread.currentThread().isInterrupted)
        }
    }

    @Test fun interruptedAfterInstallCannotInitializeLibraries() {
        val startup = BundledEngineStartup()
        onWorker {
            assertThrows(InterruptedIOException::class.java) {
                startup.initialize({ Thread.currentThread().interrupt() }, { fail("Interrupted libraries") })
            }
            assertTrue(Thread.currentThread().isInterrupted)
        }
        assertEquals(pin, startup.initialize({}, {}))
    }

    @Test fun interruptedAfterLibrariesCannotPublishReadyPin() {
        val startup = BundledEngineStartup()
        onWorker {
            assertThrows(InterruptedIOException::class.java) {
                startup.initialize({}, { Thread.currentThread().interrupt() })
            }
            assertTrue(Thread.currentThread().isInterrupted)
        }
        var retried = false
        assertEquals(pin, startup.initialize({ retried = true }, {}))
        assertTrue(retried)
    }

    @Test fun androidAdapterBindsSharedGateToAssetPrivatePathAndAtomicRename() {
        val source = adapterSource()
        assertTrue(source.substringAfter("companion object {").substringBefore("internal class BundledEngineStartup")
            .contains("private val startup = BundledEngineStartup()"))
        val init = source.substringAfter("private fun initBlocking()").substringBefore("private fun bundledLib")
        assertTrue(init.contains("startup.initialize("))
        assertTrue(init.contains("context.assets.open(BundledEngineStartup.ASSET)"))
        assertTrue(init.contains("YoutubeDL.baseName"))
        assertTrue(init.contains("YoutubeDL.ytdlpDirName"))
        assertTrue(init.contains("YoutubeDL.ytdlpBin"))
        assertTrue(init.contains("Os.rename(staged.absolutePath, target.absolutePath)"))
        assertTrue(init.indexOf("installer.install(") < init.indexOf("YoutubeDL.getInstance().init(context)"))
        assertTrue(init.indexOf("YoutubeDL.getInstance().init(context)") < init.indexOf("FFmpeg.getInstance().init(context)"))
        assertFalse(source.contains("YoutubeDL.getInstance().version(context)"))
        assertFalse(source.contains("updateYoutubeDL("))
        assertFalse(source.contains("renameTo("))
    }

    @Test fun resolveAndDownloadStillEnterStartupBoundaryAndKeepRuntimeVersionsObservable() {
        val source = adapterSource()
        val resolve = source.substringAfter("private suspend fun resolveInternal(").substringBefore("override suspend fun download(")
        val download = source.substringAfter("override suspend fun download(").substringBefore("override fun cancel(")
        assertTrue(resolve.indexOf("ensureInitialized()") in 0 until resolve.indexOf("execution.run("))
        assertTrue(download.indexOf("ensureInitialized()") in 0 until download.indexOf("downloader.download("))
        assertTrue(source.contains("ytdlpVersion = cached.ytdlpVersion ?: pin.ytdlpVersion"))
        assertTrue(source.contains("ejsVersion = cached.ejsVersion ?: pin.ejsVersion"))
        assertTrue(source.contains("ytdlpVersion = parsed.ytdlpVersion ?: base.ytdlpVersion"))
    }

    private fun adapterSource() = File(module, "src/main/java/io/github/muzammilawan/clipharbor/engine/YoutubeDlEngine.kt").readText()

    private fun install(destination: File) = installer().install(
        destination, BundledEngineStartup.SHA256, BundledEngineStartup.BYTES,
    ) { asset.inputStream() }

    private fun installer() = BundledEngineInstaller({ staged, target ->
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    })

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun onWorker(block: () -> Unit) {
        val task = FutureTask<Unit> { try { block() } finally { Thread.interrupted() } }
        val worker = Thread(task)
        worker.start()
        try { task.get(5, TimeUnit.SECONDS) } finally { worker.interrupt(); worker.join(5_000) }
        assertFalse(worker.isAlive)
    }
}
