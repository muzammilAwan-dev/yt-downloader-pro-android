package io.github.muzammilawan.clipharbor.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.muzammilawan.clipharbor.core.TaskStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric keeps this on the JVM: no emulator, no androidTest source set, runs in testDebugUnitTest.
 * API 29 matches the primary phone baseline (Infinix X682B, Android 10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TaskRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private var fakeNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = TaskRepository(db.taskDao(), clockMs = { fakeNow })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createVideo() = runBlocking {
        repository.create(
            url = "https://example.com/watch?v=abc",
            mediaMode = TaskMediaMode.VIDEO,
            requestedResolution = 1080,
            audioFormatId = null,
            mergeContainer = "mp4/mkv",
        )
    }

    private fun createAudio() = runBlocking {
        repository.create(
            url = "https://example.com/watch?v=abc",
            mediaMode = TaskMediaMode.AUDIO,
            requestedResolution = 0,
            audioFormatId = "140",
            mergeContainer = "mp4",
        )
    }

    @Test
    fun `create writes a QUEUED row with progress zero and no artifact`() = runBlocking {
        val id = createVideo()
        val task = repository.byId(id)
        assertNotNull(task)
        assertEquals(TaskStage.QUEUED, task!!.stage)
        assertEquals(0f, task.progress, 0.001f)
        assertEquals(TaskMediaMode.VIDEO, task.mediaMode)
        assertEquals(1080, task.requestedResolution)
        assertNull(task.audioFormatId)
        assertNull(task.errorCode)
        assertNull(task.artifactUri)
        assertEquals(fakeNow, task.createdAt)
        assertEquals(fakeNow, task.updatedAt)
    }

    @Test
    fun `audio only carries a validated format id and no resolution`() = runBlocking {
        val id = createAudio()
        val task = repository.byId(id)!!
        assertEquals(TaskMediaMode.AUDIO, task.mediaMode)
        assertEquals(0, task.requestedResolution)
        assertEquals("140", task.audioFormatId)
    }

    @Test
    fun `create rejects http and non loopback`() = runBlocking {
        try {
            repository.create("http://example.com/x", TaskMediaMode.VIDEO, 720, null, "mp4")
            fail("http must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `create rejects audio without a format id`() = runBlocking {
        try {
            repository.create("https://e.com/x", TaskMediaMode.AUDIO, 0, null, "mp4")
            fail("audio without format id must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `advance walks the documented forward chain and rejects skips`() = runBlocking {
        val id = createVideo()
        repository.advance(id, TaskStage.RESOLVING)
        repository.advance(id, TaskStage.DOWNLOADING)
        repository.advance(id, TaskStage.MERGING)
        repository.advance(id, TaskStage.POST_PROCESSING)
        repository.advance(id, TaskStage.PUBLISHING)
        assertEquals(TaskStage.PUBLISHING, repository.byId(id)!!.stage)
        try {
            repository.advance(id, TaskStage.QUEUED)
            fail("backward advance must throw")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `progress only writes during DOWNLOADING`() = runBlocking {
        val id = createVideo()
        repository.progress(id, 42f)
        assertEquals(0f, repository.byId(id)!!.progress, 0.001f) // QUEUED: ignored
        repository.advance(id, TaskStage.RESOLVING)
        repository.advance(id, TaskStage.DOWNLOADING)
        repository.progress(id, 42f)
        assertEquals(42f, repository.byId(id)!!.progress, 0.001f)
    }

    @Test
    fun `retry is gated by the retryable flag`() = runBlocking {
        val id = createVideo()
        repository.fail(id, "HTTP_403_ON_MEDIA")
        assertEquals(TaskStage.FAILED, repository.byId(id)!!.stage)
        try {
            repository.retry(id, retryable = false)
            fail("non-retryable retry must throw")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        repository.retry(id, retryable = true)
        assertEquals(TaskStage.QUEUED, repository.byId(id)!!.stage)
    }

    @Test
    fun `pause and resume round trip`() = runBlocking {
        val id = createVideo()
        repository.advance(id, TaskStage.RESOLVING)
        repository.advance(id, TaskStage.DOWNLOADING)
        repository.pause(id)
        assertEquals(TaskStage.PAUSED, repository.byId(id)!!.stage)
        repository.retry(id, retryable = true) // PAUSED -> QUEUED is allowed regardless of retryable
        assertEquals(TaskStage.QUEUED, repository.byId(id)!!.stage)
    }

    @Test
    fun `cancel on terminal is a no op`() = runBlocking {
        val id = createVideo()
        repository.cancel(id)
        assertEquals(TaskStage.CANCELED, repository.byId(id)!!.stage)
        repository.cancel(id) // terminal: no throw, no change
        assertEquals(TaskStage.CANCELED, repository.byId(id)!!.stage)
    }

    @Test
    fun `publish requires a publishable URI and sets DONE`() = runBlocking {
        val id = createVideo()
        repository.advance(id, TaskStage.RESOLVING)
        repository.advance(id, TaskStage.DOWNLOADING)
        repository.advance(id, TaskStage.MERGING)
        repository.advance(id, TaskStage.POST_PROCESSING)
        repository.advance(id, TaskStage.PUBLISHING)
        try {
            repository.publish(id, "https://evil.example/x")
            fail("non-content/file URI must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        repository.publish(id, "content://media/external/downloads/123")
        val task = repository.byId(id)!!
        assertEquals(TaskStage.DONE, task.stage)
        assertEquals("content://media/external/downloads/123", task.artifactUri)
    }

    @Test
    fun `observeAll emits newest first`() = runBlocking {
        fakeNow = 1_000L
        val first = createVideo()
        fakeNow = 2_000L
        val second = createVideo()
        val list = repository.observeAll().first()
        assertEquals(listOf(second, first), list.map { it.id })
        assertTrue(list.all { it.stage == TaskStage.QUEUED })
    }
}
