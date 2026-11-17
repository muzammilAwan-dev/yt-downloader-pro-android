package io.github.muzammilawan.clipharbor.feature.queue

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.muzammilawan.clipharbor.core.TaskStage
import io.github.muzammilawan.clipharbor.download.AppDatabase
import io.github.muzammilawan.clipharbor.download.TaskEntity
import io.github.muzammilawan.clipharbor.download.TaskMediaMode
import io.github.muzammilawan.clipharbor.download.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [QueueViewModel] surfaces the Room-backed durable queue as a StateFlow.
 * Read-only path: this test never writes through the view model, only through [TaskRepository].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class QueueViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var scope: CoroutineScope
    private lateinit var viewModel: QueueViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .setQueryExecutor(Dispatchers.Unconfined.asExecutor())
            .setTransactionExecutor(Dispatchers.Unconfined.asExecutor())
            .build()
        repository = TaskRepository(db.taskDao())
        scope = CoroutineScope(Dispatchers.Unconfined)
        viewModel = QueueViewModel(scope, repository)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `empty database yields an empty task list`() = runBlocking {
        assertEquals(emptyList<TaskEntity>(), viewModel.tasks.value)
    }

    @Test
    fun `creating a task makes it appear in the state flow`() = runBlocking {
        repository.create(
            url = "https://example.invalid/watch?v=x",
            mediaMode = TaskMediaMode.VIDEO,
            requestedResolution = 1080,
            audioFormatId = null,
            mergeContainer = "mp4/mkv",
        )
        val tasks = withTimeout(5_000) { viewModel.tasks.first { it.isNotEmpty() } }
        assertEquals(1, tasks.size)
        assertEquals(TaskStage.QUEUED, tasks.single().stage)
        assertEquals(TaskMediaMode.VIDEO, tasks.single().mediaMode)
        assertEquals(1080, tasks.single().requestedResolution)
    }

    @Test
    fun `stage transitions are reflected in the flow`() = runBlocking {
        val id = repository.create(
            url = "https://example.invalid/watch?v=x",
            mediaMode = TaskMediaMode.VIDEO,
            requestedResolution = 720,
            audioFormatId = null,
            mergeContainer = "mp4/mkv",
        )
        repository.advance(id, TaskStage.RESOLVING)
        val tasks = withTimeout(5_000) {
            viewModel.tasks.first { it.singleOrNull()?.stage == TaskStage.RESOLVING }
        }
        assertEquals(TaskStage.RESOLVING, tasks.single().stage)
    }
}
