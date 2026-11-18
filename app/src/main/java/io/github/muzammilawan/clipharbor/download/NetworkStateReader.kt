package io.github.muzammilawan.clipharbor.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.muzammilawan.clipharbor.core.NetworkState

/**
 * Reads the active network's metered flag. Requires `ACCESS_NETWORK_STATE` (already inside the permission budget).
 * Any read failure returns [NetworkState.UNKNOWN] instead of claiming the phone is offline.
 */
class NetworkStateReader(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun read(): NetworkState = runCatching {
        val active = manager?.activeNetwork ?: return NetworkState(connected = false, metered = false)
        val capabilities = manager.getNetworkCapabilities(active)
            ?: return NetworkState(connected = false, metered = false)
        NetworkState(
            connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }.getOrDefault(NetworkState.UNKNOWN)
}
