package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.*
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AudioHomeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=audio"
    private val aac = FormatInfo("140", "m4a", "none", "mp4a", approxBytes = 7_900_000)
    private val opus = FormatInfo("251", "webm", "none", "opus")
    private val video = FormatInfo("18", "mp4", "avc1", "mp4a", 1280, 720)
    private var now = 0L
    private var serial = 0
    private var request: DownloadRequest? = null
    private var hold = false
    private var reject = false
    private var wrongOutput = false
    private var publishGate: CompletableDeferred<Unit>? = null
    private var publishedMime: String? = null
    private var publishes = 0
    private val events = mutableListOf<String>()
    private val fake = FakeEngine()
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            it.copy(resolvedSnapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}", clockMs = { now }))
        }
        override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit, onLine: (String) -> Unit): Result<DownloadOutcome> {
            this@AudioHomeTest.request = request
            events += "engine"
            if (hold) awaitCancellation()
            val audio = request.audioPlan
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "synthetic.${if (wrongOutput) "mp4" else audio?.extension ?: "mp4"}").apply { writeText("fixture") }
            onProgress(ProgressEvent(42f, 1, "private source text"))
            return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList(),
                selectedFormatIds = listOf(audio?.formatId ?: "18"), reusedResolve = true))
        }
    }
    private val foreground = object : DownloadForeground {
        override suspend fun acquire(taskId: String): DownloadForeground.Lease {
            events += "acquire:$taskId"
            if (reject) error("rejected")
            return object : DownloadForeground.Lease {
                override fun progress(percent: Int) { events += "progress:$percent" }
                override fun publishing() { events += "publishing" }
                override fun close() { events += "release:$taskId" }
            }
        }
    }
    private fun formats(values: List<FormatInfo>) {
        fake.resolveResult = Result.success(FakeEngine.SAMPLE_RESOLVE.copy(media = FakeEngine.SAMPLE_MEDIA.copy(formats = values)))
    }
    private fun controller(scope: CoroutineScope) = HomeController(engine, scope, temporary.newFolder(),
        publish = { file -> publishes++; PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4") },
        publishAudio = { file, mime ->
            publishes++; publishedMime = mime; publishGate?.await()
            PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", mime)
        }, observer = object : HomeObserver {
            override fun event(task: String?, stage: String, event: String, fields: Map<String, String>) {
                events += "event=$event " + fields.entries.joinToString(" ") { "${it.key}=${it.value}" }
            }
        }, clockMs = { now }, newTaskId = { "task-${++serial}" }, foreground = foreground)
    private suspend fun HomeController.choose() {
        initialize(); yield(); editUrl(url); resolve(); yield()
    }

    @Test fun audioAndVideoSelectionsAreExclusiveAndDoNotStartWork(): Unit = runTest {
        formats(listOf(video, aac, opus))
        val home = controller(this); home.choose()
        assertEquals(VideoQuality.P720, home.state.value.selectedQuality)
        assertNull(home.state.value.selectedAudioFormatId)
        assertEquals(listOf("140", "251"), home.state.value.audioChoices.map { it.formatId })
        home.selectAudio("140")
        assertNull(home.state.value.selectedQuality)
        assertEquals("Download audio only", home.state.value.downloadLabel)
        assertTrue(home.state.value.canDownload)
        home.selectAudio("--exec=bad")
        assertEquals("140", home.state.value.selectedAudioFormatId)
        assertNull(request)
        home.selectQuality(VideoQuality.P720)
        assertNull(home.state.value.selectedAudioFormatId)
        assertEquals("Download video + audio", home.state.value.downloadLabel)
        home.close()
    }

    @Test fun audioOnlyCatalogueCanDownloadAndPublishWithoutVideoDimensions(): Unit = runTest {
        formats(listOf(aac))
        val home = controller(this); home.choose()
        assertEquals(HomePhase.CHOOSING, home.state.value.phase)
        assertNull(home.state.value.selectedQuality)
        assertEquals("140", home.state.value.selectedAudioFormatId)
        assertNull(request)
        home.download(); advanceUntilIdle()
        assertNotNull(request!!.audioPlan); assertNull(request!!.qualityPlan)
        assertEquals("audio/mp4", publishedMime)
        assertEquals(HomePhase.DONE, home.state.value.phase)
        assertTrue(home.state.value.status.contains("Original audio"))
        assertFalse(home.state.value.status.contains("dimensions"))
        assertEquals(1, publishes); assertFalse(request!!.outputDirectory.exists())
        assertTrue(events.indexOf("acquire:task-1") < events.indexOf("engine"))
        assertTrue(events.indexOf("publishing") < events.indexOf("release:task-1"))
        home.close()
    }

    @Test fun webmAudioUsesAudioMimeNotVideoMime(): Unit = runTest {
        formats(listOf(video, opus))
        val home = controller(this); home.choose(); home.selectAudio("251"); home.download(); advanceUntilIdle()
        assertEquals("audio/webm", publishedMime)
        assertEquals("audio/webm", home.state.value.saved!!.mimeType)
        home.close()
    }

    @Test fun wrongAudioOutputCannotReachPublisher(): Unit = runTest {
        formats(listOf(aac)); wrongOutput = true
        val home = controller(this); home.choose(); home.download(); advanceUntilIdle()
        assertEquals(HomePhase.FAILED, home.state.value.phase); assertEquals(0, publishes)
        assertTrue(events.contains("release:task-1")); home.close()
    }

    @Test fun audioCancelAndTimeoutKeepSharedCleanupBoundaries(): Unit = runTest {
        formats(listOf(aac)); hold = true
        val home = controller(this); home.choose(); home.download(); runCurrent()
        home.cancel(); advanceUntilIdle()
        assertEquals(HomePhase.CANCELED, home.state.value.phase); assertEquals(0, publishes)
        assertEquals(1, events.count { it == "release:task-1" })
        assertThrows(IllegalStateException::class.java) { request!!.resolvedSnapshot!!.withFile(url, "task-1", temporary.newFolder()) { error("closed") } }
        home.editUrl(url); home.resolve(); advanceUntilIdle(); home.download(); runCurrent()
        home.onForegroundTimeout("task-2") { events += "stop" }; advanceUntilIdle()
        assertEquals(HomePhase.PAUSED, home.state.value.phase)
        assertEquals(ErrorCode.FGS_TIMEOUT, home.state.value.pauseReason)
        assertTrue(home.state.value.audioChoices.isEmpty()); assertNull(home.state.value.selectedAudioFormatId)
        assertEquals(1, events.count { it == "release:task-2" }); home.close()
    }

    @Test fun audioSavingIsStillNotCancelable(): Unit = runTest {
        formats(listOf(aac)); publishGate = CompletableDeferred()
        val home = controller(this); home.choose(); home.download(); runCurrent()
        assertEquals(HomePhase.PUBLISHING, home.state.value.phase)
        home.cancel(); assertEquals(HomePhase.PUBLISHING, home.state.value.phase)
        publishGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.DONE, home.state.value.phase)
        assertEquals(1, publishes); home.close()
    }

    @Test fun expiredAudioSelectionRefreshesAndRequiresNewConfirmation(): Unit = runTest {
        formats(listOf(aac))
        val home = controller(this); home.choose(); now = ResolvedSnapshot.MAX_AGE_MS + 1
        home.download(); advanceUntilIdle()
        assertNull(request); assertEquals(2, fake.resolveCalls)
        assertEquals(HomePhase.CHOOSING, home.state.value.phase); home.close()
    }

    @Test fun audioAdmissionRejectionDoesNotDownloadOrPublish(): Unit = runTest {
        formats(listOf(aac)); reject = true
        val home = controller(this); home.choose(); home.download(); advanceUntilIdle()
        assertNull(request); assertEquals(0, publishes)
        assertEquals(HomePhase.FAILED, home.state.value.phase); home.close()
    }

    @Test fun diagnosticsLogReportedResolutionAndAudioModeWithoutRawSourceText(): Unit = runTest {
        formats(listOf(video.copy(width = 2560, height = 1440), aac.copy(note = "PRIVATE_NOTE")))
        val home = controller(this); home.choose(); home.selectAudio("140"); home.download(); advanceUntilIdle()
        val text = events.joinToString("\n")
        assertTrue(text.contains("reportedMaxResolution=1440"))
        assertTrue(text.contains("visibleResolutions=1440"))
        assertTrue(text.contains("event=select_audio"))
        assertTrue(text.contains("mediaMode=audio"))
        assertFalse(text.contains(url)); assertFalse(text.contains("PRIVATE_NOTE"))
        home.editUrl("https://example.invalid/next")
        assertTrue(home.state.value.audioChoices.isEmpty()); assertNull(home.state.value.selectedAudioFormatId)
        assertFalse(home.state.value.canDownload); home.close()
    }
}
