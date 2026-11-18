package io.github.muzammilawan.clipharbor

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

/** Offline manifest contract only; Android share resolution and rendered UI still need a device check. */
class ShareEntryResourcesTest {
    @Test fun previewBuildIsNotMislabeledAsThePhase0Spike() {
        assertEquals(2, BuildConfig.VERSION_CODE)
        assertEquals("0.1.0-phase1-preview", BuildConfig.VERSION_NAME)
        assertEquals("1-preview", io.github.muzammilawan.clipharbor.diagnostics.ReportContextFactory.PHASE)
    }
    private val module = sequenceOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val manifest = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newDocumentBuilder().parse(File(module, "src/main/AndroidManifest.xml"))
    private val android = "http://schemas.android.com/apk/res/android"

    @Test fun shareTargetIsSingleActivityTextOnlyWithoutCommandsOrMultipleSend() {
        val activities = manifest.getElementsByTagName("activity")
        assertEquals(1, activities.length)
        val activity = activities.item(0) as Element
        assertEquals(".MainActivity", activity.getAttributeNS(android, "name"))
        assertEquals("singleTask", activity.getAttributeNS(android, "launchMode"))
        val actions = activity.getElementsByTagName("action")
        val names = (0 until actions.length).map { (actions.item(it) as Element).getAttributeNS(android, "name") }
        assertEquals(setOf("android.intent.action.MAIN", "android.intent.action.SEND"), names.toSet())
        val data = activity.getElementsByTagName("data")
        assertEquals(1, data.length)
        val payload = data.item(0) as Element
        assertEquals("text/plain", payload.getAttributeNS(android, "mimeType"))
        assertFalse(payload.hasAttributeNS(android, "scheme"))
    }

    @Test fun foregroundServiceIsPrivateAndPermissionsStayWithinTheTaskOnlyBudget() {
        val permissions = manifest.getElementsByTagName("uses-permission")
        assertEquals(5, permissions.length)
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.POST_NOTIFICATIONS"),
            (0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(android, "name") }.toSet())
        val services = manifest.getElementsByTagName("service")
        assertEquals(1, services.length)
        val service = services.item(0) as Element
        assertEquals(".download.DownloadService", service.getAttributeNS(android, "name"))
        assertEquals("false", service.getAttributeNS(android, "exported"))
        assertEquals("dataSync", service.getAttributeNS(android, "foregroundServiceType"))
        assertFalse(service.hasAttributeNS(android, "process"))
        assertEquals(0, service.getElementsByTagName("intent-filter").length)
        val receivers = manifest.getElementsByTagName("receiver")
        assertEquals(1, receivers.length)
        val receiver = receivers.item(0) as Element
        assertEquals(".download.DownloadCancelReceiver", receiver.getAttributeNS(android, "name"))
        assertEquals("false", receiver.getAttributeNS(android, "exported"))
        assertFalse(receiver.hasAttributeNS(android, "process"))
        assertEquals(0, receiver.getElementsByTagName("intent-filter").length)
        assertEquals("false", (manifest.getElementsByTagName("application").item(0) as Element)
            .getAttributeNS(android, "usesCleartextTraffic"))
    }
}
