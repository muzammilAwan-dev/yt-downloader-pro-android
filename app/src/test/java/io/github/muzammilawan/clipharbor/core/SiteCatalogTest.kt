package io.github.muzammilawan.clipharbor.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the in-app shortcut list honest against the checked-in site contract. */
class SiteCatalogTest {
    private fun contractsFile(): File =
        sequenceOf(File("contracts/sites.json"), File("../contracts/sites.json")).first { it.isFile }

    @Test
    fun shortcutIdsMatchTheSiteContractExactlyOnce() {
        val text = contractsFile().readText()
        val contractIds = Regex("\"id\"\\s*:\\s*\"([a-z]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(contractIds, SiteCatalog.sites.map { it.id })
    }

    @Test
    fun everyShortcutOpensOnlyHttpsHomePages() {
        SiteCatalog.sites.forEach { site ->
            assertTrue(site.id, site.homeUrl.startsWith("https://"))
            assertTrue(site.id, site.homeUrl.removePrefix("https://").isNotBlank())
            assertTrue(site.id, !site.homeUrl.contains('?'))
        }
        assertEquals(SiteCatalog.sites.size, SiteCatalog.sites.map { it.id }.toSet().size)
    }
}
