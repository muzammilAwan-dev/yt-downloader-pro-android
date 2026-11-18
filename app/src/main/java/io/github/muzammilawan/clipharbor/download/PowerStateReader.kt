package io.github.muzammilawan.clipharbor.download

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

/** Minimal power facts for [io.github.muzammilawan.clipharbor.core.FragmentPolicy]; no permission is needed. */
data class PowerState(val powerSave: Boolean, val batteryPercent: Int?)

class PowerStateReader(context: Context) {
    private val appContext = context.applicationContext

    fun read(): PowerState = runCatching {
        val power = appContext.getSystemService(PowerManager::class.java)
        val battery = appContext.getSystemService(BatteryManager::class.java)
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        PowerState(powerSave = power?.isPowerSaveMode == true, batteryPercent = level)
    }.getOrDefault(PowerState(powerSave = false, batteryPercent = null))
}
