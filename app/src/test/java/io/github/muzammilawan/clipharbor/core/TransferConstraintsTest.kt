package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferConstraintsTest {
    private val onlineWifi = NetworkState(connected = true, metered = false)
    private val onlineMobile = NetworkState(connected = true, metered = true)
    private val offline = NetworkState(connected = false, metered = false)

    @Test fun wifiOnlyBlocksMeteredConnectionsOnly() {
        val constraints = TransferConstraints(wifiOnly = true)
        assertNull(constraints.blockReason(onlineWifi))
        assertTrue(constraints.permits(onlineWifi))
        assertTrue(constraints.blockReason(onlineMobile)!!.contains("metered"))
        assertFalse(constraints.permits(onlineMobile))
    }

    @Test fun withoutWifiOnlyMobileDataIsAllowed() {
        val constraints = TransferConstraints()
        assertNull(constraints.blockReason(onlineMobile))
        assertNull(constraints.blockReason(onlineWifi))
    }

    @Test fun offlineBlocksBothPreferencesWithItsOwnMessage() {
        listOf(TransferConstraints(), TransferConstraints(wifiOnly = true)).forEach { constraints ->
            val reason = constraints.blockReason(offline)
            assertTrue(reason!!.contains("No network connection"))
        }
    }

    @Test fun unknownStateNeverInventsABlock() {
        assertNull(TransferConstraints(wifiOnly = true).blockReason(NetworkState.UNKNOWN))
        assertEquals(true, NetworkState.UNKNOWN.connected)
    }
}
