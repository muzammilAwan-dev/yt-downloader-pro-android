package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.*
import io.github.muzammilawan.clipharbor.download.*
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeQueueTest {
    @get:Rule val temp = TemporaryFolder()
    private val store = MemoryTaskStore()
    private val queue = TaskRepository(store)
    private val fake = FakeEngine()
    private var serial = 0
    private var downloads = 0
    private var publishes = 0
    private var holdDownload = false
    private var failDownload = false
    private val publishGate = CompletableDeferred<Unit>()
    private var holdPublish = false
    private var space: StorageSpace? = null
    private val phasesAtWork = mutableListOf<TaskPhase>()
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String): Result<ResolveOutcome> {
            phasesAtWork += store.rows.value.last().phase
            return fake.resolve(url)
        }
        override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit): Result<DownloadOutcome> {
            downloads++
            phasesAtWork += store.rows.value.last().phase
            if (holdDownload) awaitCancellation()
            if (failDownload) return Result.failure(EngineFailure(DownloadError(ErrorCode.HTTP_403_ON_MEDIA, Stage.DOWNLOADING)))
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "test.mp4").apply { writeText("bytes") }
            return Result.success(DownloadOutcome(listOf(file), 0, 1, emptyList(), listOf("18"), 360, selectedWidth = 640))
        }
    }

    private fun home(scope: kotlinx.coroutines.CoroutineScope, repository: TaskRepository = queue) =
        HomeController(engine, scope, temp.newFolder(), publish = { file ->
            publishes++
            phasesAtWork += store.rows.value.last().phase
            if (holdPublish) publishGate.await()
            PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4")
        }, newTaskId = { "task-${++serial}" }, queue = repository,
            storageSpace = { space ?: StorageSpace(Long.MAX_VALUE, Long.MAX_VALUE) })

    @Test fun storagePreflightBlocksTransferAndRechecksActualPublicationBytes() = runTest {
        val home = home(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/a"); home.resolve(); advanceUntilIdle()
        space = StorageSpace(0, 0)
        home.download(); advanceUntilIdle()
        assertEquals(0, downloads)
        assertEquals("NO_SPACE", store.rows.value.single().reason)
        assertFalse(home.state.value.storageReport!!.allowed)
        space = StorageSpace(Long.MAX_VALUE, Long.MAX_VALUE)
        home.reviewTask("task-1"); advanceUntilIdle()
        holdDownload = false
        home.download(); advanceUntilIdle()
        assertEquals(1, downloads)
        assertEquals(1, publishes)
        assertTrue(home.state.value.storageReport!!.allowed)
        home.close()
    }

    @Test fun stageSeveralLinksWithoutEngineWorkAndExplicitReviewPersistsBeforeResolveDownloadSave() = runTest {
        val home = home(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/a"); home.stageCurrentLink(); advanceUntilIdle()
        home.editUrl("https://example.invalid/b"); home.stageCurrentLink(); advanceUntilIdle()
        assertEquals(2, store.rows.value.size)
        assertEquals(0, fake.resolveCalls)
        home.reviewTask("task-2"); advanceUntilIdle()
        assertEquals(TaskPhase.CHOOSING, store.rows.value.last().phase)
        home.selectQuality(VideoQuality.P720)
        home.download(); advanceUntilIdle()
        assertEquals(listOf(TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING), phasesAtWork)
        assertEquals(TaskPhase.DONE, store.rows.value.last().phase)
        assertEquals(720, store.rows.value.last().selection.videoHeight)
        assertEquals("content://test/1", store.rows.value.last().savedUri)
        assertEquals(TaskPhase.STAGED, store.rows.value.first().phase)
        assertEquals(1, publishes)
        home.close()
    }

    @Test fun failedMediaRetryReextractsRequiresNewConfirmationAndKeepsOnlyOneRow() = runTest {
        val home = home(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/a"); home.resolve(); advanceUntilIdle()
        failDownload = true
        home.download(); advanceUntilIdle()
        assertEquals("HTTP_403_ON_MEDIA", store.rows.value.single().reason)
        failDownload = false
        home.reviewTask(store.rows.value.single().id); advanceUntilIdle()
        assertEquals(2, fake.resolveCalls)
        assertEquals(1, downloads)
        assertEquals(HomePhase.CHOOSING, home.state.value.phase)
        home.download(); advanceUntilIdle()
        assertEquals(TaskPhase.DONE, store.rows.value.single().phase)
        home.reviewTask(store.rows.value.single().id); advanceUntilIdle()
        assertEquals(2, downloads)
        home.close()
    }

    @Test fun startupRecoveryIsProcessOnceAndNeverAutoStartsStagedOrInterruptedWork() = runTest {
        store.insert(TaskRecord("staged", "https://example.invalid/a"))
        store.insert(TaskRecord("interrupted", "https://example.invalid/a", phase = TaskPhase.DOWNLOADING))
        val first = home(this)
        first.initialize(); advanceUntilIdle()
        assertEquals(TaskPhase.PAUSED, store.rows.value.last().phase)
        assertEquals(0, fake.resolveCalls)
        store.insert(TaskRecord("live", "https://example.invalid/a", phase = TaskPhase.DOWNLOADING))
        val second = home(this)
        second.initialize(); advanceUntilIdle()
        assertEquals(TaskPhase.DOWNLOADING, store.rows.value.last().phase)
        first.close(); second.close()
    }

    @Test fun missingRowsAndInterruptedPublicationNeverStartImplicitDuplicateAttempts() = runTest {
        store.insert(TaskRecord("saving", "https://example.invalid/a", phase = TaskPhase.PUBLISHING))
        val home = home(this)
        home.initialize(); advanceUntilIdle()
        home.reviewTask("missing"); advanceUntilIdle()
        assertEquals(HomePhase.READY, home.state.value.phase)
        home.reviewTask("saving"); advanceUntilIdle()
        assertEquals(0, fake.resolveCalls)
        assertTrue(home.state.value.status.contains("Check Downloads"))
        home.reviewTask("saving", acceptPublicationRetry = true); advanceUntilIdle()
        assertEquals(1, fake.resolveCalls)
        assertEquals(0, downloads)
        home.close()
    }

    @Test fun failedDatabaseWritePreventsEngineAndPublicationWork() = runTest {
        val broken = object : TaskStore by store {
            override suspend fun insert(record: TaskRecord) { throw IllegalStateException("synthetic DB full") }
        }
        val home = home(this, TaskRepository(broken))
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/a"); home.resolve(); advanceUntilIdle()
        assertEquals(0, fake.resolveCalls)
        assertEquals(0, downloads)
        assertEquals(0, publishes)
        assertEquals(HomePhase.FAILED, home.state.value.phase)
        home.close()
    }

    @Test fun notificationCancelAndTimeoutPersistOnlyAfterTaskCleanupAndSavingIsFenced() = runTest {
        val home = home(this)
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/a"); home.resolve(); advanceUntilIdle()
        holdDownload = true
        home.download(); runCurrent()
        home.onForegroundTimeout("task-1") {}
        assertEquals(HomePhase.PAUSING, home.state.value.phase)
        advanceUntilIdle()
        assertEquals(TaskPhase.PAUSED, store.rows.value.single().phase)
        assertEquals("FGS_TIMEOUT", store.rows.value.single().reason)
        holdDownload = false
        home.reviewTask("task-1"); advanceUntilIdle()
        holdPublish = true
        home.download(); runCurrent()
        home.cancel(); runCurrent()
        assertEquals(TaskPhase.PUBLISHING, store.rows.value.single().phase)
        publishGate.complete(Unit); advanceUntilIdle()
        assertEquals(TaskPhase.DONE, store.rows.value.single().phase)
        home.close()
    }
}
