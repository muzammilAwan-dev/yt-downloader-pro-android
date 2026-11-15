package io.github.muzammilawan.clipharbor.log

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RotatingLogTest {
    private lateinit var dir: java.io.File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("clipharbor-log").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun writesRedactedLinesToAppNamedFile() {
        val log = RotatingLog(dir, maxBytesPerFile = 10_000, maxFiles = 5, clock = { 0L })
        log.append("Cookie: SAPISID=SECRETVALUE")
        log.append("task=1 stage=RESOLVING event=ok")
        log.close()

        val files = log.files()
        assertEquals(1, files.size)
        assertEquals("app-19700101-000000.log", files.first().name)
        val text = files.first().readText()
        assertFalse(text.contains("SECRETVALUE"))
        assertTrue(text.contains("task=1 stage=RESOLVING event=ok"))
    }

    @Test
    fun rotationKeepsAtMostMaxFiles() {
        val log = RotatingLog(dir, maxBytesPerFile = 200, maxFiles = 3, clock = { 0L })
        log.fileHeader = "== DIAGNOSTIC REPORT ==\nCookie: SAPISID=HEADER_SECRET"
        repeat(80) { i ->
            log.append("line=$i stage=DOWNLOADING event=progress " + "x".repeat(30))
        }
        log.close()

        val files = log.files()
        assertTrue("expected rotation, got ${files.map { it.name }}", files.size in 2..3)
        assertTrue(files.all { it.name.startsWith("app-") && it.name.endsWith(".log") })
        assertTrue(files.all { it.readText().startsWith("== DIAGNOSTIC REPORT ==") })
        assertTrue(files.none { it.readText().contains("HEADER_SECRET") })
        assertTrue(files.all { it.length() <= 200 })
        assertTrue(files.any { it.readText().contains("line=79") })
    }

    @Test
    fun ensureOpenCreatesAHeaderOnlyLogThatCanBeSharedImmediately() {
        val log = RotatingLog(dir, maxBytesPerFile = 10_000, maxFiles = 5, clock = { 0L })
        log.fileHeader = "== DIAGNOSTIC REPORT ==\napp=ClipHarbor"

        log.ensureOpen()
        log.close()

        assertEquals("== DIAGNOSTIC REPORT ==\napp=ClipHarbor\n", log.files().single().readText())
    }

    @Test
    fun utf8LinesRespectTheByteLimitWithoutSplittingCharacters() {
        val log = RotatingLog(dir, maxBytesPerFile = 200, maxFiles = 3, clock = { 0L })
        log.fileHeader = "== DIAGNOSTIC REPORT ==\napp=ClipHarbor"
        repeat(4) { log.append("event=progress title=" + "🤭".repeat(200)) }
        log.close()

        val files = log.files()
        assertTrue(files.size in 1..3)
        assertTrue(files.all { it.length() <= 200 })
        assertTrue(files.all { it.readText().contains("[truncated]") })
        assertFalse(files.any { it.readText().contains('\uFFFD') })
    }

    @Test
    fun filesFromSessionsWithinTheSameSecondHaveDistinctNames() {
        val log = RotatingLog(dir, clock = { 0L })
        log.append("event=first")
        log.close()
        log.append("event=second")
        log.close()

        assertEquals(
            setOf("app-19700101-000000.log", "app-19700101-000000-2.log"),
            log.files().map { it.name }.toSet(),
        )
    }

    @Test
    fun clearRemovesEveryLogFile() {
        val log = RotatingLog(dir, maxBytesPerFile = 100, maxFiles = 3, clock = { 0L })
        repeat(20) { log.append("line=$it " + "y".repeat(40)) }
        log.close()
        assertTrue(log.files().isNotEmpty())

        log.clear()
        assertTrue(log.files().isEmpty())
    }

    @Test
    fun timestampIsUtcWithMillis() {
        assertEquals("1970-01-01T00:00:00.000Z", RotatingLog.timestamp(0L))
    }
}
