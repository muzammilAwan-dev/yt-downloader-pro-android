package io.github.muzammilawan.clipharbor.publish

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class PublicationProviderTest {
    @Test fun legacyProviderRootsAreNarrowAndExcludeStagingReplayAndBroadPaths() {
        val module = sequenceOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").isFile }
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(module, "src/main/res/xml/file_paths.xml"))
        val roots = xml.documentElement.childNodes
        val paths = (0 until roots.length).map { roots.item(it) }.filter { it.nodeType == org.w3c.dom.Node.ELEMENT_NODE }
        assertEquals(setOf("logs/", "Download/YTDLP/", "YTDLP/"), paths.map { it.attributes.getNamedItem("path").nodeValue }.toSet())
        assertTrue(paths.all { it.nodeName in setOf("files-path", "external-files-path") })
    }
}
