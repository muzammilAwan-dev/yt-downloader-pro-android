package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.core.FormatInfo
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeControllerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=test"
    private var publishes = 0
    private var now = 0L
    private var serial = 0
    private var request: DownloadRequest? = null
    private var holdDownload = false
    private var holdPublish = false
    private var downloadedWidth: Int? = 640
    private var downloadedHeight: Int? = 360
    private val progressCallbacks = mutableListOf<(ProgressEvent) -> Unit>()
    private val publishing = CompletableDeferred<Unit>()
    private val fake = FakeEngine()
    private val snapshot = ResolvedSnapshot(url, "task-1", "{\"formats\":[]}", clockMs = { now })
    private val engine = object : io.github.muzammilawan.clipharbor.core.Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            it.copy(resolvedSnapshot = if (processId == "task-1") snapshot
                else ResolvedSnapshot(url, processId, "{\"formats\":[]}", clockMs = { now }))
        }
        override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit): Result<DownloadOutcome> {
            this@HomeControllerTest.request = request
            progressCallbacks += onProgress
            if (holdDownload) awaitCancellation()
            onProgress(ProgressEvent(42f, 1, "synthetic"))
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "synthetic.mp4").apply { writeText("synthetic bytes") }
            return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList(), listOf("18"), downloadedHeight, true,
                selectedWidth = downloadedWidth))
        }
    }

    private fun controller(scope: CoroutineScope) = HomeController(engine, scope, temporary.newFolder(), publish = { file ->
        publishes++
        if (holdPublish) publishing.await()
        PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4")
    }, clockMs = { now }, newTaskId = { "task-${++serial}" })

    @Test fun incomingShareOnlyPrefillsUntilUserConfirms() = runTest {
        val home = controller(this)
        home.receiveShared("Title\n$url")
        assertEquals(url, home.state.value.url)
        assertEquals(0, fake.resolveCalls)
        home.initialize()
        advanceUntilIdle()
        assertEquals(HomePhase.READY, home.state.value.phase)
        assertNull(request)
        home.close()
    }

    @Test fun invalidInputNeverCallsEngine() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl("http://example.invalid/a --exec bad")
        home.resolve(); advanceUntilIdle()
        assertNotNull(home.state.value.inputError)
        assertEquals(0, fake.resolveCalls)
        home.close()
    }

    @Test fun downloadNeedsChoiceAndReportsActualRatherThanCatalogMaximum() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url)
        home.download()
        assertNull(request)
        home.resolve(); advanceUntilIdle()
        assertEquals(VideoQuality.entries.reversed(), home.state.value.qualities.map { it.quality })
        assertEquals(listOf(VideoQuality.P1080, VideoQuality.P720, VideoQuality.P360),
            home.state.value.qualities.filter { it.available }.map { it.quality })
        assertEquals(VideoQuality.P1080, home.state.value.selectedQuality)
        home.selectQuality(VideoQuality.P2160)
        assertEquals(VideoQuality.P1080, home.state.value.selectedQuality)
        home.selectQuality(VideoQuality.P720)
        home.download(); advanceUntilIdle()
        assertEquals(720, request!!.maxHeight)
        assertEquals(VideoQuality.P720, request!!.qualityPlan!!.quality)
        assertEquals("mp4/mkv", request!!.mergeContainer)
        assertSame(snapshot, request!!.resolvedSnapshot)
        assertEquals(HomePhase.DONE, home.state.value.phase)
        assertTrue(home.state.value.status.contains("360p"))
        assertFalse(home.state.value.status.contains("1080p"))
        assertEquals(1, publishes)
        assertFalse(request!!.outputDirectory.exists())
        home.close()
    }

    @Test fun editingAndDismissingClosePrivateInput() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.editUrl("https://example.invalid/other")
        assertEquals(HomePhase.READY, home.state.value.phase)
        assertTrue(home.state.value.qualities.isEmpty())
        assertThrows(IllegalStateException::class.java) {
            snapshot.withFile(url, "task-1", temporary.newFolder()) { error("must not run") }
        }
        home.close()
    }

    @Test fun expiredChooserRefreshesButRequiresDownloadConfirmationAgain() = runTest {
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        now = ResolvedSnapshot.MAX_AGE_MS + 1
        home.download(); advanceUntilIdle()
        assertEquals(2, fake.resolveCalls)
        assertNull(request)
        assertEquals(HomePhase.CHOOSING, home.state.value.phase)
        home.close()
    }

    @Test fun cancelDoesNotPublishAndNewShareDoesNotReplaceActiveSource() = runTest {
        holdDownload = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        home.receiveShared("https://example.invalid/new")
        assertEquals(url, home.state.value.url)
        assertEquals("https://example.invalid/new", home.state.value.pendingUrl)
        home.cancel(); advanceUntilIdle()
        assertEquals(HomePhase.CANCELED, home.state.value.phase)
        assertEquals(0, publishes)
        assertEquals(listOf("task-1"), fake.cancelledProcessIds)
        home.usePendingLink()
        assertEquals("https://example.invalid/new", home.state.value.url)
        assertNull(home.state.value.pendingUrl)
        home.close()
    }

    @Test fun duplicateTapsCannotResolveOrDownloadTwice() = runTest {
        val home = controller(this)
        home.initialize(); home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); home.resolve(); advanceUntilIdle()
        home.download(); home.download(); advanceUntilIdle()
        assertEquals(1, fake.initCalls)
        assertEquals(1, fake.resolveCalls)
        assertEquals(1, publishes)
        home.close()
    }

    @Test fun publishIsANonCancelableCommitBoundary() = runTest {
        holdPublish = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertEquals(HomePhase.PUBLISHING, home.state.value.phase)
        home.cancel()
        assertEquals(HomePhase.PUBLISHING, home.state.value.phase)
        publishing.complete(Unit); advanceUntilIdle()
        assertEquals(HomePhase.DONE, home.state.value.phase)
        home.close()
    }

    @Test fun loginFailureIsHonestAndDoesNotExposeExceptionSecrets() = runTest {
        fake.resolveResult = Result.failure(EngineFailure(DownloadError(ErrorCode.LOGIN_REQUIRED, Stage.RESOLVING,
            detail = "Cookie: secret_token")))
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        assertEquals(HomePhase.FAILED, home.state.value.phase)
        assertTrue(home.state.value.status.contains("not available yet"))
        assertFalse(home.state.value.status.contains("secret_token"))
        assertFalse(home.state.value.toString().contains(url))
        assertEquals(0, publishes)
        home.close()
    }

    @Test fun failedInitializationCanBeRetried() = runTest {
        fake.initResult = Result.failure(IllegalStateException("synthetic failure"))
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        assertEquals(HomePhase.FAILED, home.state.value.phase)
        assertFalse(home.state.value.engineReady)
        fake.initResult = Result.success(FakeEngine.SAMPLE_INFO)
        home.initialize(); advanceUntilIdle()
        assertTrue(home.state.value.engineReady)
        home.close()
    }

    @Test fun lateProgressFromCanceledTaskCannotOverwriteNewTask() = runTest {
        holdDownload = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        home.cancel(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        assertEquals(2, progressCallbacks.size)
        progressCallbacks.first()(ProgressEvent(99f, 0, "late old progress"))
        assertNull(home.state.value.progress)
        progressCallbacks.last()(ProgressEvent(15f, 0, "current progress"))
        assertEquals(15, home.state.value.progress)
        home.close(); advanceUntilIdle()
        progressCallbacks.last()(ProgressEvent(80f, 0, "late after close"))
        assertEquals(15, home.state.value.progress)
        assertEquals(0, publishes)
    }

    @Test fun closingActiveDownloadCancelsAndClosesPrivateChooserInput() = runTest {
        holdDownload = true
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); runCurrent()
        home.close(); advanceUntilIdle()
        assertEquals(listOf("task-1"), fake.cancelledProcessIds)
        assertEquals(0, publishes)
        assertThrows(IllegalStateException::class.java) {
            snapshot.withFile(url, "task-1", temporary.newFolder()) { error("must not run") }
        }
    }

    @Test fun portraitResultUsesShortEdgeQualityAndReportsBothDimensions() = runTest {
        fake.resolveResult = Result.success(FakeEngine.SAMPLE_RESOLVE.copy(media = FakeEngine.SAMPLE_MEDIA.copy(
            formats = listOf(FormatInfo("18", "mp4", "avc1", "mp4a", 720, 1280)))))
        downloadedWidth = 720; downloadedHeight = 1280
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        assertEquals(VideoQuality.P720, home.state.value.selectedQuality)
        home.download(); advanceUntilIdle()
        assertEquals(720, request!!.qualityPlan!!.quality.pixels)
        assertTrue(home.state.value.status.contains("720 × 1280 (720p short edge)"))
        assertFalse(home.state.value.status.contains("1280p"))
        home.close()
    }

    @Test fun highQualityMergesUseMkvWithoutPretendingToTranscode() = runTest {
        fake.resolveResult = Result.success(FakeEngine.SAMPLE_RESOLVE.copy(media = FakeEngine.SAMPLE_MEDIA.copy(
            formats = listOf(FormatInfo("18", "webm", "vp9", "opus", 2560, 1440)))))
        downloadedWidth = 2560; downloadedHeight = 1440
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        assertEquals(VideoQuality.P1440, home.state.value.selectedQuality)
        home.download(); advanceUntilIdle()
        assertEquals("mkv", request!!.mergeContainer)
        assertEquals(VideoQuality.P1440, request!!.qualityPlan!!.quality)
        home.close()
    }

    @Test fun unknownDownloadedDimensionsAreNotInferredFromChoice() = runTest {
        downloadedWidth = null; downloadedHeight = null
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        home.download(); advanceUntilIdle()
        assertTrue(home.state.value.status.contains("dimensions not fully reported"))
        assertFalse(home.state.value.status.contains("1080p"))
        home.close()
    }

    @Test fun unsupportedCatalogueDoesNotCreateADownloadableChooser() = runTest {
        fake.resolveResult = Result.success(FakeEngine.SAMPLE_RESOLVE.copy(media = FakeEngine.SAMPLE_MEDIA.copy(
            formats = listOf(FormatInfo("orphan", "mp4", "avc1", "none", 1280, 720)))))
        val home = controller(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl(url); home.resolve(); advanceUntilIdle()
        assertEquals(HomePhase.FAILED, home.state.value.phase)
        assertNull(home.state.value.selectedQuality)
        home.download(); advanceUntilIdle()
        assertNull(request)
        assertEquals(0, publishes)
        home.close()
    }
}
