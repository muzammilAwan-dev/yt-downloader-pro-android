package io.github.muzammilawan.clipharbor.feature.home

import io.github.muzammilawan.clipharbor.core.DownloadForeground
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.ResolvedSnapshot
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pre-flight free-space gate: blocked downloads stay in CHOOSING with a warning status so the user can free
 * space and retry with one tap; the resolved snapshot is preserved. Unknown free space (null) means "cannot
 * measure"; the check is skipped rather than blocking a legitimate download.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeControllerFreeSpaceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val url = "https://example.invalid/watch?v=space"
    private var freeSpace: Long? = null
    private var serial = 0
    private var downloads = 0
    private var lastRequest: DownloadRequest? = null
    private val fake = FakeEngine()
    private val engine = object : Engine by fake {
        override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).map {
            it.copy(resolvedSnapshot = ResolvedSnapshot(url, processId, "{\"formats\":[]}"))
        }
        override suspend fun download(
            request: DownloadRequest,
            onProgress: (ProgressEvent) -> Unit,
            onLine: (String) -> Unit,
        ): Result<DownloadOutcome> {
            downloads++
            lastRequest = request
            onProgress(ProgressEvent(50f, 0, "synthetic"))
            val audio = request.audioPlan
            val ext = audio?.extension ?: "mp4"
            request.outputDirectory.mkdirs()
            val file = File(request.outputDirectory, "synthetic.$ext").apply { writeText("bytes") }
            return Result.success(DownloadOutcome(
                files = listOf(file),
                exitCode = 0,
                elapsedMs = 1,
                outputTail = emptyList(),
                selectedFormatIds = listOf(audio?.formatId ?: "18"),
                selectedHeight = if (audio != null) null else 360,
                reusedResolve = true,
                selectedWidth = if (audio != null) null else 640,
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

    private fun controller(scope: CoroutineScope) = HomeController(
        engine, scope, temporary.newFolder(),
        publish = { file -> PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", "video/mp4") },
        publishAudio = { file, mime -> PublishedMedia("content://test/1", file.name, "Downloads/YTDLP", mime) },
        clockMs = { 0L },
        newTaskId = { "task-${++serial}" },
        foreground = foreground,
        freeSpaceMb = { freeSpace },
    )

    /**
     * Chained the same way HomeControllerTest does it: advance between each coroutine-launching call.
     * Without this, [HomeController.editUrl] bails on `busy` (phase is INITIALIZING) and [HomeController.resolve]
     * bails on `!engineReady` (init has not run yet); the later download() then sees no prepared snapshot.
     */
    private fun TestScope.readyToChoose(home: HomeController) {
        home.initialize()
        advanceUntilIdle()
        home.editUrl(url)
        home.resolve()
        advanceUntilIdle()
    }

    @Test
    fun `sufficient free space allows video download`() = runTest {
        freeSpace = 5_000
        val home = controller(this)
        try {
            readyToChoose(home)
            home.download(); advanceUntilIdle()
            assertEquals(1, downloads)
            assertEquals(HomePhase.DONE, home.state.value.phase)
            assertNotNull(lastRequest?.qualityPlan)
        } finally {
            home.close()
        }
    }

    @Test
    fun `insufficient free space blocks video download before the engine runs`() = runTest {
        freeSpace = 100
        val home = controller(this)
        try {
            readyToChoose(home)
            home.download(); advanceUntilIdle()
            assertEquals(0, downloads)
            // Warning state, not a hard failure: stays CHOOSING so one tap after freeing space retries.
            assertEquals(HomePhase.CHOOSING, home.state.value.phase)
            assertTrue(home.state.value.status.contains("Not enough free storage"))
            assertTrue(home.state.value.canDownload)
        } finally {
            home.close()
        }
    }

    @Test
    fun `after freeing space the same prepared snapshot is reused for one-tap retry`() = runTest {
        freeSpace = 100
        val home = controller(this)
        try {
            readyToChoose(home)
            home.download(); advanceUntilIdle()
            assertEquals(0, downloads)
            // No re-resolve needed: snapshot preserved and expiry window not exceeded.
            val resolvesBefore = fake.resolveCalls
            freeSpace = 5_000
            home.download(); advanceUntilIdle()
            assertEquals(resolvesBefore, fake.resolveCalls)
            assertEquals(1, downloads)
            assertEquals(HomePhase.DONE, home.state.value.phase)
        } finally {
            home.close()
        }
    }

    @Test
    fun `unknown free space is treated as unknown and does not block`() = runTest {
        freeSpace = null
        val home = controller(this)
        try {
            readyToChoose(home)
            home.download(); advanceUntilIdle()
            assertEquals(1, downloads)
            assertEquals(HomePhase.DONE, home.state.value.phase)
        } finally {
            home.close()
        }
    }

    @Test
    fun `audio uses the lower threshold so 500 MB passes audio but blocks video`() = runTest {
        freeSpace = 500

        // Video: blocked (needs 1024 MB default).
        val videoHome = controller(this)
        try {
            readyToChoose(videoHome)
            videoHome.download(); advanceUntilIdle()
            assertEquals(0, downloads)
            assertEquals(HomePhase.CHOOSING, videoHome.state.value.phase)
        } finally {
            videoHome.close()
        }

        // Audio: passes (needs 256 MB default).
        fake.resolveResult = Result.success(
            FakeEngine.SAMPLE_RESOLVE.copy(
                media = FakeEngine.SAMPLE_MEDIA.copy(
                    formats = listOf(FakeEngine.SAMPLE_FORMATS.first { it.formatId == "140" }),
                ),
            ),
        )
        val audioHome = controller(this)
        try {
            audioHome.initialize()
            advanceUntilIdle()
            audioHome.editUrl(url)
            audioHome.resolve()
            advanceUntilIdle()
            assertNotNull(audioHome.state.value.selectedAudioFormatId)
            audioHome.download(); advanceUntilIdle()
            assertEquals(1, downloads)
            assertEquals(HomePhase.DONE, audioHome.state.value.phase)
            assertNotNull(lastRequest?.audioPlan)
        } finally {
            audioHome.close()
        }
    }
}
