package io.github.muzammilawan.clipharbor.log

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.Engine
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.feature.home.HomeController
import io.github.muzammilawan.clipharbor.feature.home.HomeObserver
import io.github.muzammilawan.clipharbor.feature.home.HomePhase
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.testing.FakeEngine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionLogTest {
    @get:Rule val temporary = TemporaryFolder()
    private val header = "== DIAGNOSTIC REPORT ==\napp=YTDLP gitSha=test"

    @Test fun exportIncludesStartupAndEveryEventEvenAfterRotatingFilesArePruned() {
        val dir = temporary.newFolder()
        val session = SessionLog(File(dir, "session"), clock = { 0L })
        val rotating = RotatingLog(File(dir, "rolling"), maxBytesPerFile = 100, maxFiles = 1, clock = { 0L })
        assertTrue(session.start(header))
        val lines = listOf("event=app_start") + (0..100).map { "event=action index=$it " + "x".repeat(50) } + "event=share_log"
        lines.forEach { session.append(it); rotating.append(it) }
        val exported = session.export(session.capture()).readText()
        assertTrue(exported.startsWith(header))
        var previous = -1
        lines.forEach { line ->
            val position = exported.indexOf(line)
            assertTrue("Missing/out-of-order event: $line", position > previous)
            previous = position
        }
        assertFalse(rotating.files().single().readText().contains("event=app_start"))
        session.close(); rotating.close()
    }

    @Test fun shareTapCutoffIsFrozenBeforeAsynchronousCopyAndLaterWritesDoNotChangeIt() {
        val session = SessionLog(temporary.newFolder())
        session.start(header)
        session.append("event=app_start")
        session.append("event=share_log")
        val cutoff = session.capture()
        session.append("event=after_tap")
        val first = session.export(cutoff)
        val text = first.readText()
        assertTrue(text.contains("event=share_log"))
        assertFalse(text.contains("event=after_tap"))
        session.append("event=another_action")
        val second = session.export(session.capture())
        assertNotEquals(first, second)
        assertEquals(text, first.readText())
        assertTrue(second.readText().contains("event=another_action"))
        session.close()
    }

    @Test fun journalAndExportRedactHeadersTokensCookiesEmailsAndSignedQueries() {
        val dir = temporary.newFolder()
        val session = SessionLog(dir)
        session.start(header + "\nCookie: HEADER_SECRET")
        session.append("Cookie: COOKIE_SECRET")
        session.append("Authorization: Bearer AUTH_SECRET")
        session.append("event=engine url=https://cdn.example.invalid/media?sig=SIGNED_SECRET")
        session.append("po_token=PO_SECRET email=owner@example.invalid")
        val exported = session.export(session.capture()).readText()
        assertTrue(exported.contains("event=engine"))
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val text = file.readText()
            listOf("HEADER_SECRET", "COOKIE_SECRET", "AUTH_SECRET", "SIGNED_SECRET", "PO_SECRET", "owner@example.invalid")
                .forEach { assertFalse("Secret in ${file.name}: $it", text.contains(it)) }
        }
        session.close()
    }

    @Test fun processRestartStartsANewTimelineWithoutPreviousProcessEvents() {
        val dir = temporary.newFolder()
        val first = SessionLog(dir)
        first.start(header); first.append("event=previous_process"); first.close()
        val next = SessionLog(dir)
        next.start(header); next.append("event=app_start")
        val text = next.export(next.capture()).readText()
        assertTrue(text.contains("session_scope=since_app_start"))
        assertTrue(text.contains("event=app_start"))
        assertFalse(text.contains("event=previous_process"))
        next.close()
    }

    @Test fun explicitClearResetsCoverageAndRejectsAPreClearCutoff() {
        val session = SessionLog(temporary.newFolder())
        session.start(header); session.append("event=before_clear")
        val stale = session.capture()
        session.clear(); session.append("event=logs_cleared")
        assertThrows(IllegalStateException::class.java) { session.export(stale) }
        val text = session.export(session.capture()).readText()
        assertTrue(text.startsWith(header))
        assertTrue(text.contains("session_scope=since_clear"))
        assertTrue(text.contains("event=logs_cleared"))
        assertFalse(text.contains("event=before_clear"))
        session.close()
    }

    @Test fun failedJournalNeverExportsAPartialFileAsACompleteSession() {
        val blocked = temporary.newFile()
        val session = SessionLog(blocked)
        assertFalse(session.start(header))
        session.append("event=unrecorded")
        assertThrows(IllegalStateException::class.java) { session.capture() }
        session.close()
    }

    @Test fun failedPrefixCopyDeletesItsPartialExportInsteadOfReturningItForSharing() {
        val dir = temporary.newFolder()
        val session = SessionLog(dir)
        session.start(header); session.append("event=app_start")
        val cutoff = session.capture()
        session.close()
        File(dir, "active.txt").writeText("short") // Simulate an unreadable/incomplete captured prefix.
        assertThrows(IllegalStateException::class.java) { session.export(cutoff) }
        assertTrue(File(dir, "exports").listFiles().orEmpty().isEmpty())
    }

    @Test fun concurrentEngineAndUiEventsAreFlushedWithoutLoss() {
        val session = SessionLog(temporary.newFolder())
        session.start(header)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val writes = (0..3).map { worker -> executor.submit {
                repeat(100) { session.append("event=worker_${worker}_$it") }
            } }
            writes.forEach { it.get(10, TimeUnit.SECONDS) }
            val text = session.export(session.capture()).readLines()
            assertEquals(400, text.count { it.startsWith("event=worker_") })
            assertEquals(400, text.filter { it.startsWith("event=worker_") }.toSet().size)
        } finally { executor.shutdownNow(); session.close() }
    }

    @Test fun fullSessionIsNotSilentlyLimitedToTheTwoMbRollingChunk() {
        val session = SessionLog(temporary.newFolder())
        session.start(header); session.append("event=app_start")
        repeat(220) { session.append("event=engine index=$it " + "x".repeat(10_000)) }
        session.append("event=share_log")
        val file = session.export(session.capture())
        assertTrue(file.length() > RotatingLog.DEFAULT_MAX_BYTES)
        val text = file.readText()
        assertTrue(text.contains("event=app_start"))
        assertTrue(text.contains("event=engine index=0 "))
        assertTrue(text.contains("event=engine index=219 "))
        assertTrue(text.contains("event=share_log"))
        assertFalse(text.contains("[truncated]"))
        session.close()
    }

    @Test fun oldShareSnapshotsArePrunedWithoutRemovingTheActiveJournal() {
        val dir = temporary.newFolder()
        val session = SessionLog(dir, clock = { 0L })
        session.start(header)
        val exports = (0..6).map { session.append("event=action_$it"); session.export(session.capture()) }
        assertEquals(5, exports.count { it.exists() })
        assertTrue(exports.last().exists())
        session.append("event=still_recording")
        assertTrue(session.export(session.capture()).readText().contains("event=action_0"))
        session.close()
    }

    @Test fun everyTypedUiActionHasAStableEventWithoutUserContentPayloads() {
        val session = SessionLog(temporary.newFolder(), clock = { 0L })
        session.start(header)
        UserAction.entries.forEach { action ->
            session.append(action.line(0, "CHOOSING", qualityPixels = 1080, enabled = false))
        }
        val text = session.export(session.capture()).readText()
        UserAction.entries.forEach { action ->
            assertTrue(text.contains("stage=UI event=${action.name.lowercase(java.util.Locale.ROOT)} phase=CHOOSING qualityPixels=1080 enabled=false"))
        }
        assertTrue(text.contains("event=share_log"))
        session.close()
    }

    @Test fun fakeEngineTimelineIncludesResolveDiagnosticsTimingsProgressAndPublicationWithSecretsRedacted() = runTest {
        var now = 0L
        val session = SessionLog(temporary.newFolder(), clock = { now })
        session.start(header); session.append("event=app_start")
        val fake = FakeEngine(resolveResult = Result.success(FakeEngine.SAMPLE_RESOLVE.copy(debugTail = listOf(
            "[debug] Cookie: RESOLVE_SECRET", "[debug] resolved_probe",
        ))))
        val engine = object : Engine by fake {
            override suspend fun resolveForTask(url: String, processId: String) = fake.resolve(url).also { now += 25 }
            override suspend fun download(request: DownloadRequest, onProgress: (ProgressEvent) -> Unit,
                onLine: (String) -> Unit): Result<DownloadOutcome> {
                onProgress(ProgressEvent(42f, 1, "secret progress text"))
                onProgress(ProgressEvent(42f, 1, "duplicate"))
                onLine("[debug] download_probe url=https://cdn.example.invalid/media?sig=DOWNLOAD_SECRET")
                request.outputDirectory.mkdirs()
                val file = File(request.outputDirectory, "test.mp4").apply { writeText("synthetic") }
                return Result.success(DownloadOutcome(listOf(file), 0, 50, emptyList(), selectedFormatIds = listOf("18")))
            }
        }
        val home = HomeController(engine, this, temporary.newFolder(), publish = {
            now += 10
            PublishedMedia("content://test/1", it.name, "Downloads/YTDLP", "video/mp4")
        }, observer = object : HomeObserver {
            override fun event(task: String?, stage: String, event: String, fields: Map<String, String>) {
                session.append("task=$task stage=$stage event=$event " + fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
            }
            override fun line(task: String, stage: String, line: String) {
                session.append("task=$task stage=$stage event=ytdlp $line")
            }
        }, clockMs = { now }, newTaskId = { "task-1" })
        home.initialize(); advanceUntilIdle()
        home.editUrl("https://example.invalid/watch?v=test"); home.resolve(); advanceUntilIdle()
        home.download(); advanceUntilIdle()
        assertEquals(HomePhase.DONE, home.state.value.phase)
        session.append(UserAction.SHARE_LOG.line(now, home.state.value.phase.name))
        val text = session.export(session.capture()).readText()
        assertTrue(text.contains("stage=RESOLVING event=ytdlp [debug] resolved_probe"))
        assertTrue(text.contains("stage=RESOLVING event=ok formats=4 ms=25"))
        assertTrue(text.contains("stage=DOWNLOADING event=engine_result exit=0 ms=50"))
        assertEquals(1, text.lineSequence().count { it.contains("stage=DOWNLOADING event=progress percent=42") })
        assertTrue(text.contains("stage=DOWNLOADING event=ytdlp [debug] download_probe"))
        assertTrue(text.lineSequence().any { it.contains("stage=PUBLISHING event=ok") && it.contains("ms=10") })
        assertFalse(text.contains("RESOLVE_SECRET"))
        assertFalse(text.contains("DOWNLOAD_SECRET"))
        assertFalse(text.contains("secret progress text"))
        home.close(); session.close()
    }
}
