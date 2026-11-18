package io.github.muzammilawan.clipharbor.download

import android.content.Context
import androidx.core.content.edit
import io.github.muzammilawan.clipharbor.core.FragmentPolicy
import io.github.muzammilawan.clipharbor.core.TransferConstraints

/**
 * Small, dependency-free preference store for transfer constraints (Phase 2B).
 * SharedPreferences is process-wide, so the Activity and the download session always agree.
 */
class TransferPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("transfer", Context.MODE_PRIVATE)

    fun constraints(): TransferConstraints = TransferConstraints(wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false))

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY, enabled) }
    }

    /** Stored `-N` setting; [FragmentPolicy.AUTO] means "decide from power state at download time". */
    fun fragments(): Int = FragmentPolicy.sanitize(prefs.getInt(KEY_FRAGMENTS, FragmentPolicy.AUTO))

    fun setFragments(setting: Int) {
        if (FragmentPolicy.valid(setting)) prefs.edit { putInt(KEY_FRAGMENTS, setting) }
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_FRAGMENTS = "concurrent_fragments"
    }
}
