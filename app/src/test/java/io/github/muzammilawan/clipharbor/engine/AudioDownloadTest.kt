package io.github.muzammilawan.clipharbor.engine

import io.github.muzammilawan.clipharbor.core.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=sample"
    private val plan = AudioFormats.plan(listOf(FormatInfo("140", "m4a", "none", "mp4a")), "140")!!
    private class Runner : BlockingEngineRunner {
        val commands = mutableListOf<EngineCommand>()
        var action: (EngineCommand) -> EngineResponse = { EngineResponse(0, "", "") }
        var destroys = 0
        override fun execute(command: EngineCommand, internalId: String, callback: ((Float, Long, String) -> Unit)?): EngineResponse {
            commands += command
            return action(command)
        }
        override fun destroy(internalId: String) { destroys++ }
    }
    private fun request(directory: File, snapshot: ResolvedSnapshot? = null) =
        DownloadRequest(url, outputDirectory = directory, processId = "task", resolvedSnapshot = snapshot, audioPlan = plan)
    private fun response(directory: File, id: String = "140", video: String = "none", audio: String = "mp4a",
        dimensions: String = "NA\tNA", extension: String = "m4a", marker: Boolean = true): EngineResponse {
        val file = File(directory, "fixture_audio.$extension").apply { writeText("synthetic audio") }
        val output = "${DownloadEvidence.SELECTION_MARKER}$id\t$dimensions\n" +
            (if (marker) "${DownloadEvidence.AUDIO_MARKER}$id\t$video\t$audio\n" else "") +
            "${DownloadEvidence.FILE_MARKER}${file.absolutePath}"
        return EngineResponse(0, output, "")
    }

    @Test fun originalAudioUsesExactSelectorNoMergeOrConversionAndReusesPrivateInput() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        var input: File? = null
        val runner = Runner().apply { action = { command ->
            input = File(command.options.single { it.first == "--load-info-json" }.second!!)
            assertTrue(input!!.isFile)
            response(directory)
        } }
        val snapshot = ResolvedSnapshot(url, "task", "{\"formats\":[]}")
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), inputs).download(request(directory, snapshot), {}, {}).getOrThrow()
        val command = runner.commands.single()
        assertTrue(command.urls.isEmpty())
        assertEquals(AudioFormats.selector(plan), command.options.single { it.first == "-f" }.second)
        assertEquals(AudioFormats.outputTemplate(), command.options.single { it.first == "-o" }.second)
        assertTrue(command.options.contains("--print" to DownloadEvidence.AUDIO_PRINT))
        assertFalse(command.options.any { it.first in setOf("--merge-output-format", "-x", "--audio-format", "--audio-quality") })
        assertEquals(listOf("140"), result.selectedFormatIds)
        assertNull(result.selectedWidth); assertNull(result.selectedHeight)
        assertEquals("m4a", result.files.single().extension)
        assertTrue(result.reusedResolve)
        assertFalse(input!!.exists())
    }

    @Test fun unsubstantiatedAudioNeverReturnsPublishableSuccess() = runBlocking {
        val directory = temporary.newFolder()
        val bad = listOf(response(directory, id = "251"), response(directory, video = "avc1"),
            response(directory, audio = "none"), response(directory, dimensions = "1920\t1080"),
            response(directory, extension = "mp4"), response(directory, marker = false), response(directory, id = "140+251"))
        for (output in bad) {
            val runner = Runner().apply { action = { output } }
            val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder()).download(request(directory), {}, {})
            assertTrue(result.isFailure)
            assertEquals(ErrorCode.POST_PROCESS_FAILED, (result.exceptionOrNull() as EngineFailure).error.code)
        }
    }

    @Test fun conflictingVideoAndAudioPlansAreRejectedBeforeRunnerAndCloseInput(): Unit = runBlocking {
        val inputs = temporary.newFolder()
        val snapshot = ResolvedSnapshot(url, "task", "{}")
        val video = VideoQualities.plan(listOf(FormatInfo("18", "mp4", "avc1", "mp4a", 1280, 720)), VideoQuality.P720)!!
        val runner = Runner()
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), inputs)
            .download(request(temporary.newFolder(), snapshot).copy(maxHeight = 720, qualityPlan = video), {}, {})
        assertTrue(result.isFailure); assertTrue(runner.commands.isEmpty())
        assertThrows(IllegalStateException::class.java) { snapshot.withFile(url, "task", inputs) { error("closed") } }
    }

    @Test fun audioEvidenceCannotSurviveLaterSelectionOrInvalidAudioMarker() {
        val evidence = DownloadEvidence()
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}140\tNA\tNA")
        evidence.accept("${DownloadEvidence.AUDIO_MARKER}140\tnone\tmp4a")
        assertEquals("140", evidence.audioFormatId())
        evidence.accept("${DownloadEvidence.SELECTION_MARKER}140\tNA\tNA")
        assertNull(evidence.audioFormatId())
        evidence.accept("${DownloadEvidence.AUDIO_MARKER}140\tnone\tmp4a")
        evidence.accept("${DownloadEvidence.AUDIO_MARKER}140\tavc1\tmp4a")
        assertNull(evidence.audioFormatId())
    }

    @Test fun webmAudioKeepsItsOriginalContainer() = runBlocking {
        val directory = temporary.newFolder()
        val opus = AudioFormats.plan(listOf(FormatInfo("251", "webm", "none", "opus")), "251")!!
        val runner = Runner().apply { action = { response(directory, id = "251", audio = "opus", extension = "webm") } }
        val result = DownloadExecutor(runner, EngineExecution(runner::destroy), temporary.newFolder())
            .download(request(directory).copy(audioPlan = opus), {}, {}).getOrThrow()
        assertEquals("webm", result.files.single().extension)
        assertEquals("audio/webm", opus.mimeType)
    }

    @Test fun audioCancellationInterruptsRunnerAndReleasesPrivateInput() = runBlocking {
        val directory = temporary.newFolder()
        val inputs = temporary.newFolder()
        val started = CountDownLatch(1)
        val runner = Runner().apply { action = { started.countDown(); CountDownLatch(1).await(); response(directory) } }
        val execution = EngineExecution(runner::destroy)
        val pending = async { DownloadExecutor(runner, execution, inputs).download(request(directory, ResolvedSnapshot(url, "task", "{}")), {}, {}) }
        yield()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(execution.cancel("task"))
        try { withTimeout(5000) { pending.await() }; fail("Canceled audio must not publish") }
        catch (_: CancellationException) { assertTrue(pending.isCancelled) }
        assertTrue(inputs.listFiles()!!.isEmpty())
        assertEquals(1, runner.destroys)
        assertFalse(execution.cancel("task"))
    }
}
