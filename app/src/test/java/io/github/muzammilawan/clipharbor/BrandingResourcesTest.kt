package io.github.muzammilawan.clipharbor

import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline resource contracts; actual OEM launcher rendering still requires a phone check. */
class BrandingResourcesTest {
    private val module = sequenceOf(File("."), File("app"))
        .first { File(it, "src/main/AndroidManifest.xml").isFile }

    private fun source(path: String) = File(module, "src/main/$path")

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newDocumentBuilder().parse(source(path))

    @Test
    fun launcherLabelMatchesAppMetadata() {
        val strings = xml("res/values/strings.xml").getElementsByTagName("string")
        val name = (0 until strings.length).map { strings.item(it) }
            .single { it.attributes.getNamedItem("name").nodeValue == "app_name" }
        assertEquals(AppInfo.DISPLAY_NAME, name.textContent)
        val application = xml("AndroidManifest.xml").getElementsByTagName("application").item(0)
        assertEquals("@string/app_name", application.attributes.getNamedItemNS(ANDROID, "label").nodeValue)
        assertEquals("@drawable/ic_launcher", application.attributes.getNamedItemNS(ANDROID, "icon").nodeValue)
    }

    @Test
    fun artworkIsTheExactOwnerSelectedPngFrameWithSafeSymbolBounds() {
        val file = source("res/drawable-nodpi/ytdlp_icon.png")
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("0221f46a21d1d739eaf4d68d5364f127049b14201d28764f4d444e4d7178e5ea", hash)
        val image = ImageIO.read(file)
        assertEquals(256, image.width)
        assertEquals(256, image.height)
        assertTrue(image.colorModel.hasAlpha())
        var symbolPixels = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val alpha = pixel ushr 24 and 0xff
                val red = pixel ushr 16 and 0xff
                val green = pixel ushr 8 and 0xff
                val blue = pixel and 0xff
                if (alpha >= 128 && red > 120 && red > green * 2 && red > blue * 2) {
                    symbolPixels++
                    // 108dp layer: the centered inner 66dp safe zone must retain the play/download symbol.
                    assertTrue(x * 108.0 / image.width in 21.0..87.0)
                    assertTrue(y * 108.0 / image.height in 21.0..87.0)
                }
            }
        }
        assertTrue(symbolPixels > 1000)
    }

    @Test
    fun legacyAndAdaptiveIconsBothReferenceOwnerArtwork() {
        val legacy = xml("res/drawable/ic_launcher.xml").documentElement
        assertEquals("bitmap", legacy.tagName)
        assertEquals("@drawable/ytdlp_icon", legacy.getAttributeNS(ANDROID, "src"))
        val adaptive = xml("res/drawable-anydpi-v26/ic_launcher.xml")
        assertEquals("adaptive-icon", adaptive.documentElement.tagName)
        assertEquals(1, adaptive.getElementsByTagName("background").length)
        assertEquals(1, adaptive.getElementsByTagName("foreground").length)
        val bitmap = adaptive.getElementsByTagName("bitmap").item(0)
        assertEquals("@drawable/ytdlp_icon", bitmap.attributes.getNamedItemNS(ANDROID, "src").nodeValue)
        assertEquals("fill", bitmap.attributes.getNamedItemNS(ANDROID, "gravity").nodeValue)
    }

    @Test
    fun adaptivePaddingPreservesDesktopScaleAtDifferentLauncherSizes() {
        val adaptive = xml("res/drawable-anydpi-v26/ic_launcher.xml")
        val foreground = adaptive.getElementsByTagName("foreground").item(0)
        val inset = adaptive.getElementsByTagName("inset")
        assertEquals(1, inset.length)
        assertEquals(foreground, inset.item(0).parentNode)
        val value = inset.item(0).attributes.getNamedItemNS(ANDROID, "inset").nodeValue
        assertTrue("Padding must scale with launcher bounds, not device density", value.endsWith("%"))
        val fraction = value.removeSuffix("%").toDouble() / 100
        // Android 10 AdaptiveIconDrawable extends each edge by 25% of the visible size.
        for (size in listOf(48, 72, 96, 144, 192, 256)) {
            val layerSize = size * 1.5
            val bitmapStart = -size * 0.25 + layerSize * fraction
            val bitmapSize = layerSize * (1 - fraction * 2)
            assertEquals("Desktop proportions at $size px", size.toDouble(), bitmapSize, 0.1)
            assertEquals("Centered image at $size px", (size - bitmapSize) / 2, bitmapStart, 0.001)
            assertTrue("Tile must not extend outside the viewport", bitmapStart >= 0)
            assertTrue(bitmapStart + bitmapSize <= size)
        }
    }

    @Test
    fun desktopIconLicenseIsIncludedInPackagedAssets() {
        val license = source("assets/licenses/ytdlp-desktop-icon-MIT.txt").readText()
        assertTrue(license.contains("MIT License"))
        assertTrue(license.contains("Copyright (c) 2026 Muhammad Muzammil"))
        assertTrue(license.contains("The above copyright notice and this permission notice shall be included"))
        assertTrue(license.contains("host/YTDLPHost/Assets/icon.ico"))
    }

    companion object {
        private const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
