package io.github.muzammilawan.clipharbor.feature.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.core.TaskStage
import io.github.muzammilawan.clipharbor.download.AppDatabase
import io.github.muzammilawan.clipharbor.download.TaskEntity
import io.github.muzammilawan.clipharbor.download.TaskMediaMode
import io.github.muzammilawan.clipharbor.download.TaskRepository
import io.github.muzammilawan.clipharbor.engine.EngineFailure
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric keeps the durable queue test on the JVM: no emulator, no androidTest source set. API 29 matches
 * the primary phone baseline. Room is built with Dispatchers.Unconfined as the query and transaction executor so
 * suspend DAO calls complete synchronously inside runBlocking; the TestScope variant would deadlock against Room's
 * internal executor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeControllerPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=test"
    private var now = 0L
    private var serial = 0
    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository

    private var holdDownload = false
    private var failDownload = false
    private var downloadedWidth: Int? = 640
    private var downloadedHeight: Int? = 360
    private val fake = FakeEngine()
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            it.copy(resolvedSnapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}", clockMs = { now }))
        }
        override suspend fun download(
            request: DownloadRequest,
            onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit,
        ): Result<DownloadOutcome> {
            if (holdDownload) awaitCancellation()
            if (failDownload) return Result.failure(
                EngineFailure(DownloadError(ErrorCode.NETWORK_TIMEOUT, Stage.DOWNLOADING)),
            )
            onProgress(ProgressEvent(50f, 1, "synthetic"))
            val audio = request.audioPlan
            val extension = audio?.extension ?: "mp4"
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "synthetic.$extension").apply { writeText("bytes") }
            return Result.success(DownloadOutcome(
                files = listOf(file),
                exitCode = 0,
                elapsedMs = 1,
                outputTail = emptyList(),
                selectedFormatIds = listOf(audio?.formatId ?: "18"),
                selectedHeight = if (audio != null) null else downloadedHeight,
                reusedResolve = true,
                selectedWidth = if (audio != null) null else downloadedWidth,
            ))
        }
    }
    private val foreground = object : DownloadForeground {
        override suspend fun acquire(taskId: String): DownloadForeground.Lease =
            object : DownloadForeground.Lease {
                override fun progress(percent: Int) {}
                override fun publishing() {}
                override fun close() {}
            }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .setQueryExecutor(Dispatchers.Unconfined.asExecutor())
            .setTransactionExecutor(Dispatchers.Unconfined.asExecutor())
            .build()
        repository = TaskRepository(db.taskDao(), clockMs = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun controller(scope: CoroutineScope) = HomeController(
        engine, scope, temporary.newFolder(),
        publish = { file -> PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4") },
        publishAudio = { file, mime -> PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", mime) },
        clockMs = { now },
        newTaskId = { "task-${++serial}" },
        foreground = foreground,
        repository = repository,
    )

    private suspend fun allRows(): List<TaskEntity> =
        db.taskDao().byStages(TaskStage.entries.toList())

    @Test
    fun `happy path persists a QUEUED row that finishes DONE with an artifact URI`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val home = controller(scope)
        try {
            home.initialize()
            home.editUrl(url)
            home.resolve()
            home.download()
            val task = allRows().single()
            assertEquals(TaskStage.DONE, task.stage)
            assertEquals(TaskMediaMode.VIDEO, task.mediaMode)
            assertEquals(1080, task.requestedResolution)
            assertNull(task.audioFormatId)
            assertEquals("content://test/1", task.artifactUri)
            assertNull(task.errorCode)
        } finally {
            home.close()
            scope.cancel()
        }
    }

    @Test
    fun `engine failure persists a FAILED row with the error code`() = runBlocking {
        failDownload = true
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val home = controller(scope)
        try {
            home.initialize()
            home.editUrl(url)
            home.resolve()
            home.download()
            val task = allRows().single()
            assertEquals(TaskStage.FAILED, task.stage)
            assertEquals(ErrorCode.NETWORK_TIMEOUT.name, task.errorCode)
            assertNull(task.artifactUri)
        } finally {
            home.close()
            scope.cancel()
        }
    }

    @Test
    fun `user cancel during download persists a CANCELED row`() = runBlocking {
        holdDownload = true
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val home = controller(scope)
        try {
            home.initialize()
            home.editUrl(url)
            home.resolve()
            home.download()
            home.cancel()
            val task = allRows().single()
            assertEquals(TaskStage.CANCELED, task.stage)
        } finally {
            home.close()
            scope.cancel()
        }
    }

    @Test
    fun `foreground timeout persists a PAUSED row with FGS_TIMEOUT reason`() = runBlocking {
        holdDownload = true
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val home = controller(scope)
        try {
            home.initialize()
            home.editUrl(url)
            home.resolve()
            home.download()
            home.onForegroundTimeout("task-1") {}
            val task = allRows().single()
            assertEquals(TaskStage.PAUSED, task.stage)
            assertEquals(ErrorCode.FGS_TIMEOUT, home.state.value.pauseReason)
        } finally {
            home.close()
            scope.cancel()
        }
    }

    @Test
    fun `audio only selection persists AUDIO mode with format id and zero resolution`() = runBlocking {
        fake.resolveResult = Result.success(
            FakeEngine.SAMPLE_RESOLVE.copy(
                media = FakeEngine.SAMPLE_MEDIA.copy(
                    formats = listOf(FakeEngine.SAMPLE_FORMATS.first { it.formatId == "140" }),
                ),
            ),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val home = controller(scope)
        try {
            home.initialize()
            home.editUrl(url)
            home.resolve()
            home.download()
            val task = allRows().single()
            assertEquals(TaskStage.DONE, task.stage)
            assertEquals(TaskMediaMode.AUDIO, task.mediaMode)
            assertEquals(0, task.requestedResolution)
            assertEquals("140", task.audioFormatId)
        } finally {
            home.close()
            scope.cancel()
        }
    }
}
