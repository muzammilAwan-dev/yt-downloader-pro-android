package io.github.muzammilawan.clipharbor.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline APK-input contracts. Does not execute Python/JavaScript or contact any site. */
class BundledEnginePayloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val module = sequenceOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val asset = File(module, "src/main/$ASSET")

    @Test fun assetMatchesSignedUpstreamReleaseExactly() {
        assertTrue("Missing pinned APK engine asset", asset.isFile)
        val bytes = asset.readBytes()
        assertEquals(SIZE, bytes.size.toLong())
        assertEquals(SHA256, hash("SHA-256", bytes))
        assertEquals("#!/usr/bin/env python3\n", bytes.copyOfRange(0, 23).toString(Charsets.UTF_8))
    }

    @Test fun archiveIncludesPinnedEngineEjsAndLazyExtractorsWithoutNewNativeCode() {
        ZipFile(asset).use { zip ->
            assertTrue(text(zip, "yt_dlp/version.py").contains("__version__ = '2026.10.26'"))
            assertTrue(text(zip, "yt_dlp_ejs/_version.py").contains("__version__ = version = '0.8.0'"))
            assertTrue(text(zip, "yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py").contains("VERSION = '0.8.0'"))
            assertNotNull(zip.getEntry("yt_dlp/extractor/lazy_extractors.py"))
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertFalse(entries.any { name -> listOf(".so", ".dll", ".exe", ".pyd").any { name.endsWith(it, ignoreCase = true) } })
        }
    }

    @Test fun bothLocalSolverScriptsMatchPinnedVendorHashes() {
        ZipFile(asset).use { zip ->
            val vendor = text(zip, "yt_dlp/extractor/youtube/jsc/_builtin/vendor/_info.py")
            for ((name, expected) in SOLVER_HASHES) {
                val entry = zip.getEntry("yt_dlp_ejs/yt/solver/$name")
                assertNotNull("Missing locally bundled solver $name", entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertEquals(expected, hash("SHA3-512", bytes))
                assertTrue(vendor.contains(expected))
            }
        }
    }

    @Test fun completeBundledDependencyLicensesArePreservedInsideSolverHeader() {
        ZipFile(asset).use { zip ->
            val header = text(zip, "yt_dlp_ejs/yt/solver/lib.min.js").substringBefore("*/")
                .lineSequence().map { it.trim().removePrefix("*").trimStart() }.joinToString("\n")
            assertTrue(header.contains("SPDX-License-Identifier: Unlicense"))
            assertTrue(header.contains("Name: meriyah\nVersion: 6.1.4\nLicense: ISC"))
            assertTrue(header.contains("Name: astring\nVersion: 1.9.0\nLicense: MIT"))
            assertTrue("Full ISC notice must ship, not just an identifier", header.contains(ISC_NOTICE))
            assertTrue("Full MIT notice must ship, not just an identifier", header.contains(MIT_NOTICE))
            assertTrue(text(zip, "yt_dlp_ejs/yt/solver/core.min.js").substringBefore("*/")
                .contains("SPDX-License-Identifier: Unlicense"))
        }
    }

    @Test fun realBundledAssetInstallsLocallyAndMatchingPinIsNoOp() {
        val destination = File(temporary.newFolder(), "yt-dlp").apply { writeText("synthetic previous engine") }
        val installer = BundledEngineInstaller({ staged, target ->
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        })
        assertEquals(BundleInstallResult.INSTALLED, installer.install(destination, SHA256, SIZE) { asset.inputStream() })
        assertEquals(SHA256, hash("SHA-256", destination.readBytes()))
        assertEquals(BundleInstallResult.ALREADY_INSTALLED,
            installer.install(destination, SHA256, SIZE) { error("Matching pin must not reopen bundle") })
        assertEquals(listOf(destination), destination.parentFile!!.listFiles()!!.toList())
    }

    @Test fun thirdPartyIndexIdentifiesTheBundledCandidateAndEmbeddedNotices() {
        val index = File(module, "../THIRD_PARTY_NOTICES.md").readText()
        assertTrue(index.contains(ASSET))
        assertTrue(index.contains("EJS 0.8.0"))
        assertTrue(index.contains("Meriyah 6.1.4"))
        assertTrue(index.contains("Astring 1.9.0"))
        assertTrue(index.contains("lib.min.js"))
        assertFalse(index.contains("verified but not bundled yet"))
    }

    private fun text(zip: ZipFile, name: String): String {
        val entry = zip.getEntry(name)
        assertNotNull("Missing archive entry $name", entry)
        return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun hash(algorithm: String, bytes: ByteArray): String = MessageDigest.getInstance(algorithm).digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val ASSET = "assets/engine/yt-dlp-2026.10.26"
        private const val SIZE = 3_073_072L
        private const val SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
        private val SOLVER_HASHES = mapOf(
            "core.min.js" to "ee5b307d07f55e91e4723edf5ac205cc877a474187849d757dc1322e38427b157a9d706d510c1723d3670f98e5a3f8cbcde77874a80406bd7204bc9fea30f283",
            "lib.min.js" to "8420c259ad16e99ce004e4651ac1bcabb53b4457bf5668a97a9359be9a998a789fee8ab124ee17f91a2ea8fd84e0f2b2fc8eabcaf0b16a186ba734cf422ad053",
        )
        private val ISC_NOTICE = """
            ISC License

            Copyright (c) 2019 and later, KFlash and others.

            Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

            THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
        """.trimIndent()
        private val MIT_NOTICE = """
            Copyright (c) 2015, David Bonnet <david@bonnet.cc>

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction, including without limitation the rights
            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
            copies of the Software, and to permit persons to whom the Software is
            furnished to do so, subject to the following conditions:

            The above copyright notice and this permission notice shall be included in
            all copies or substantial portions of the Software.

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
            THE SOFTWARE.
        """.trimIndent()
    }
}
