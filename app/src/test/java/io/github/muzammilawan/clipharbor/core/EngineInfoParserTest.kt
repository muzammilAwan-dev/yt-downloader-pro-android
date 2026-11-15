package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineInfoParserTest {
    private val verbose = listOf(
        "[debug] Command-line config: ['-v', '--dump-json', '--no-playlist', 'https://example.invalid/watch?v=x']",
        "[debug] yt-dlp version stable@2025.11.12 from yt-dlp (zip)",
        "[debug] Python 3.8.20 (CPython arm64 64bit) - Linux-6.1-android",
        "[debug] exe versions: ffmpeg 7.1 (setts), ffprobe 7.1",
        "[debug] Optional libraries: sqlite3-3.40.1",
        "[debug] JS runtimes: quickjs-2021-03-27",
        "[debug] yt-dlp-ejs 0.3.0",
        "[debug] Proxy map: {}",
    )

    @Test
    fun parsesTheVersionsWeNeedForTheReport() {
        val info = EngineInfoParser.parse(verbose, libraryVersion = "0.18.1")
        assertEquals("0.18.1", info.libraryVersion)
        assertEquals("2025.11.12", info.ytdlpVersion)
        assertEquals("3.8.20", info.pythonVersion)
        assertEquals("7.1", info.ffmpegVersion)
        assertEquals("7.1", info.ffprobeVersion)
        assertEquals("quickjs-2021-03-27", info.jsRuntime)
        assertEquals("0.3.0", info.ejsVersion)
        assertTrue(info.hasJsRuntime())
        assertTrue(info.hasFfmpeg())
    }

    @Test
    fun toleratesMissingAndReorderedFields() {
        val info = EngineInfoParser.parse(listOf("[debug] something else entirely"), libraryVersion = null)
        assertNull(info.ytdlpVersion)
        assertNull(info.jsRuntime)
        assertNull(info.ffmpegVersion)
        assertNull(info.libraryVersion)
        assertTrue(info.headerLine().contains("jsRuntime=missing"))
    }

    @Test
    fun acceptsVersionWithoutChannelPrefix() {
        val info = EngineInfoParser.parse(listOf("[debug] yt-dlp version 2026.10.26"), null)
        assertEquals("2026.10.26", info.ytdlpVersion)
    }
}
