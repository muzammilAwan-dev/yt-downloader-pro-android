package io.github.muzammilawan.clipharbor.core

/** What the phone's connectivity currently looks like; supplied by the Android adapter, never guessed here. */
data class NetworkState(val connected: Boolean, val metered: Boolean) {
    companion object {
        /** Used only when the platform cannot be read; allowing the attempt is safer than a false block. */
        val UNKNOWN = NetworkState(connected = true, metered = false)
    }
}

/**
 * Explicit transfer preference (Phase 2B "Wi-Fi only"). It only blocks the deliberate Download tap:
 * resolving a link stays allowed, so the user can still see what a link offers on mobile data.
 */
data class TransferConstraints(val wifiOnly: Boolean = false) {
    /** Null when the transfer may start; otherwise a user-facing explanation. */
    fun blockReason(state: NetworkState): String? = when {
        !state.connected -> "No network connection. Connect to the internet, then confirm Download again."
        wifiOnly && state.metered ->
            "Wi-Fi only is on and this connection is metered. Connect to Wi-Fi, or choose “Use mobile data” to allow this one download."
        else -> null
    }

    fun permits(state: NetworkState): Boolean = blockReason(state) == null
}
