package io.github.muzammilawan.clipharbor.engine

import io.github.muzammilawan.clipharbor.core.DownloadEvidence
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.FormatInfo
import io.github.muzammilawan.clipharbor.core.VideoQualities
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.core.FormatPlanner
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadExecutorTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=sample"

    private class Runner : BlockingEngineRunner {
        var action: (EngineCommand, String, ((Float, Long, String) -> Unit)?) -> EngineResponse = { _, _, _ -> EngineResponse(0, "", "") }
        val commands = mutableListOf<EngineCommand>()
        val internalIds = mutableListOf<String>()
        val destroyed = mutableListOf<String>()
        override fun execute(command: EngineCommand, internalId: String, callback: ((Float, Long, String) -> Unit)?): EngineResponse {
            commands += command
            internalIds += internalId
            return action(command, internalId, callback)
        }
        override fun destroy(internalId: String) { destroyed += internalId }
    }

    private fun request(directory: File, snapshot: ResolvedSnapshot? = null, task: String = "task") =
        DownloadRequest(url, outputDirectory = directory, processId = task, resolvedSnapshot = snapshot)

    private fun success(directory: File): EngineResponse {
        val file = File(directory, "fixture.mp4").apply { writeText("synthetic media") }
        return EngineResponse(0, "${DownloadEvidence.SELECTION_MARKER}137+140\t1080\n${DownloadEvidence.FILE_MARKER}${file.absolutePath}", "")
    }

    @Test
    fun replayUsesEmptyUrlsFreshSelectorAndBoundedFragmentsThenDeletesInput() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        val runner = Runner()
        var input: File? = null
        runner.action = { command, _, _ ->
            assertTrue(command.urls.isEmpty())
            assertTrue(command.redirectErrorStream)
            val options = command.options
            assertEquals("4", options.single { it.first == "-N" }.second)
            assertTrue(options.single { it.first == "-f" }.second!!.contains("height<=1080"))
            assertEquals(2, options.count { it.first == "--print" })
            assertTrue(options.contains("--clean-info-json" to null))
            input = File(options.single { it.first == "--load-info-json" }.second!!)
            assertTrue(input!!.exists())
            assertTrue(input!!.readText().contains("formats"))
            success(directory)
        }
        val snapshot = ResolvedSnapshot(url, "task", """{"formats":[{"format_id":"18"}]}""")
        val execution = EngineExecution(runner::destroy)
        val result = DownloadExecutor(runner, execution, inputs).download(request(directory, snapshot), {}, {})
        val outcome = result.getOrThrow()
        assertTrue(outcome.reusedResolve)
        assertEquals(listOf("137", "140"), outcome.selectedFormatIds)
        assertEquals(1080, outcome.selectedHeight)
        assertFalse(input!!.exists())
        assertFalse(execution.cancel("task"))
    }

    @Test
    fun freshDownloadUsesOnlySourceUrl() = runBlocking {
        val directory = temporary.newFolder()
        val runner = Runner().apply { action = { _, _, _ -> success(directory) } }
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder()).download(request(directory), {}, {})
        assertFalse(result.getOrThrow().reusedResolve)
        assertEquals(listOf(url), runner.commands.single().urls)
        assertFalse(runner.commands.single().options.any { it.first == "--load-info-json" })
        assertFalse(runner.commands.single().toString().contains(url))
    }

    @Test fun standardPlanUsesCappedIdsQualityFilenameAndBothDimensions() = runBlocking {
        val directory = temporary.newFolder()
        val plan = VideoQualities.plan(listOf(FormatInfo("portrait", "mp4", "avc1", "mp4a", 1080, 1920)), VideoQuality.P1080)!!
        val runner = Runner().apply { action = { _, _, _ ->
            val file = File(directory, "synthetic_up-to-1080p.mp4").apply { writeText("fixture") }
            EngineResponse(0, "${DownloadEvidence.SELECTION_MARKER}portrait\t1080\t1920\n${DownloadEvidence.FILE_MARKER}${file.absolutePath}", "")
        } }
        val outcome = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder())
            .download(request(directory).copy(qualityPlan = plan), {}, {}).getOrThrow()
        val options = runner.commands.single().options
        assertEquals(FormatPlanner.selector(plan), options.single { it.first == "-f" }.second)
        assertEquals(FormatPlanner.outputTemplate(plan.quality), options.single { it.first == "-o" }.second)
        assertEquals("mp4/mkv", options.single { it.first == "--merge-output-format" }.second)
        assertEquals(1080, outcome.selectedWidth)
        assertEquals(1920, outcome.selectedHeight)
        assertTrue(options.contains("--windows-filenames" to null))
        assertFalse(options.any { it.first == "--embed-thumbnail" || it.first == "--restrict-filenames" })
    }

    @Test fun unsubstantiatedPlanCannotReturnPublishableSuccess() = runBlocking {
        val directory = temporary.newFolder()
        val plan = VideoQualities.plan(listOf(FormatInfo("v", "mp4", "avc1", "mp4a", 1280, 720)), VideoQuality.P720)!!
        for (selection in listOf("v\t1920\t1080", "unlisted\t1280\t720", "v\tNA\t720")) {
            val runner = Runner().apply { action = { _, _, _ ->
                val file = File(directory, "synthetic.mp4").apply { writeText("fixture") }
                EngineResponse(0, "${DownloadEvidence.SELECTION_MARKER}$selection\n${DownloadEvidence.FILE_MARKER}${file.absolutePath}", "")
            } }
            val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder())
                .download(request(directory).copy(maxHeight = 720, qualityPlan = plan), {}, {})
            assertEquals(ErrorCode.POST_PROCESS_FAILED, (result.exceptionOrNull() as EngineFailure).error.code)
        }
        val runner = Runner()
        assertTrue(DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder())
            .download(request(directory).copy(qualityPlan = plan), {}, {}).isFailure)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun taskResolveRemainsCompatibleWithEnginesWithoutReplay() = runBlocking {
        val engine = FakeEngine()
        val resolved = engine.resolveForTask(url, "task").getOrThrow()
        assertEquals(1, engine.resolveCalls)
        assertEquals(FakeEngine.SAMPLE_MEDIA, resolved.media)
        assertNull(resolved.resolvedSnapshot)
    }

    @Test
    fun thrownLibraryFailureKeepsRedactedBoundedTailAndUnknownExit() = runBlocking {
        val runner = Runner().apply {
            action = { _, _, callback ->
                repeat(220) { callback!!(-1f, -1, "[debug] line $it Cookie: private-cookie") }
                callback!!(-1f, -1, "ERROR: HTTP Error 403: Forbidden https://cdn.invalid/file?sig=private-signature")
                throw IOException("")
            }
        }
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder()).download(request(temporary.newFolder()), {}, {})
        val error = (result.exceptionOrNull() as EngineFailure).error
        assertEquals(ErrorCode.HTTP_403_ON_MEDIA, error.code)
        assertNull(error.exitCode)
        assertEquals(200, error.stderrTail.size)
        assertFalse(error.stderrTail.joinToString().contains("private-cookie"))
        assertFalse(error.stderrTail.joinToString().contains("private-signature"))
    }

    @Test
    fun nonzeroExitCannotPublishEvenWithFinalMarker() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        val runner = Runner().apply { action = { _, _, _ -> val good = success(directory); EngineResponse(7, good.out, "ERROR: Conversion failed!") } }
        val snapshot = ResolvedSnapshot(url, "task", "{}")
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), inputs).download(request(directory, snapshot), {}, {})
        assertTrue(result.isFailure)
        assertEquals(7, (result.exceptionOrNull() as EngineFailure).error.exitCode)
        assertTrue(inputs.listFiles()!!.isEmpty())
    }

    @Test
    fun consumerFailureNeverEscapesReaderButFailsTask() = runBlocking {
        val directory = temporary.newFolder()
        var readerContinued = false
        val runner = Runner().apply {
            action = { _, _, callback ->
                callback!!(10f, 2, "[download] 10%")
                readerContinued = true
                success(directory)
            }
        }
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder()).download(request(directory), {}, { throw IllegalStateException("logger closed") })
        assertTrue(readerContinued)
        assertEquals(ErrorCode.POST_PROCESS_FAILED, (result.exceptionOrNull() as EngineFailure).error.code)
    }

    @Test
    fun stalePercentOnUnrelatedLineIsNotProgress() = runBlocking {
        val directory = temporary.newFolder()
        val percentages = mutableListOf<Float?>()
        val runner = Runner().apply {
            action = { _, _, callback ->
                callback!!(100f, 0, "[Merger] Merging formats")
                callback(10f, 2, "[download] 10%")
                success(directory)
            }
        }
        DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder()).download(request(directory), { percentages += it.percent }, {}).getOrThrow()
        assertEquals(listOf(null, 10f), percentages)
    }

    @Test
    fun invalidSourcesNeverReachProcessRunner() = runBlocking {
        val runner = Runner()
        val executor = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder())
        listOf("http://example.invalid", "file:///data/private", "--exec=bad", "https://user:password@example.invalid", "https://example.invalid/\n").forEach {
            assertTrue(executor.download(request(temporary.newFolder()).copy(url = it), {}, {}).isFailure)
        }
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun taskCancellationInterruptsWorkerDeletesSnapshotAndDoesNotReturnSuccess() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        val started = CountDownLatch(1)
        val runner = Runner().apply {
            action = { _, _, _ ->
                started.countDown()
                CountDownLatch(1).await()
                success(directory)
            }
        }
        val execution = EngineExecution(runner::destroy)
        val snapshot = ResolvedSnapshot(url, "task;not-a-shell-key", "{}")
        val pending = async { DownloadExecutor(runner, execution, inputs).download(request(directory, snapshot, "task;not-a-shell-key"), {}, {}) }
        // async must enter before the main thread waits on the latch.
        kotlinx.coroutines.yield()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(execution.cancel("task;not-a-shell-key"))
        try {
            withTimeout(5000) { pending.await() }
            fail("Cancellation must not report success")
        } catch (_: CancellationException) {
            assertTrue(pending.isCancelled)
        }
        assertTrue(inputs.listFiles()!!.isEmpty())
        assertFalse(execution.cancel("task;not-a-shell-key"))
        assertTrue(runner.internalIds.single().matches(Regex("[a-f0-9-]{36}")))
        assertEquals(runner.internalIds, runner.destroyed)
    }

    @Test
    fun workerIgnoringInterruptionStillCannotReportSuccessAfterCancellation() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        val started = CountDownLatch(1)
        val runner = Runner().apply {
            action = { _, _, _ ->
                started.countDown()
                try { CountDownLatch(1).await() } catch (_: InterruptedException) { /* synthetic uncooperative runner */ }
                success(directory)
            }
        }
        val execution = EngineExecution(runner::destroy)
        val snapshot = ResolvedSnapshot(url, "task", "{}")
        val pending = async { DownloadExecutor(runner, execution, inputs).download(request(directory, snapshot), {}, {}) }
        kotlinx.coroutines.yield()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(execution.cancel("task"))
        try {
            withTimeout(5000) { pending.await() }
            fail("Canceled runner result must not be publishable")
        } catch (_: CancellationException) {
            assertTrue(pending.isCancelled)
        }
        assertTrue(inputs.listFiles()!!.isEmpty())
        assertFalse(execution.cancel("task"))
    }
}
