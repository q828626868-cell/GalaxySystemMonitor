package com.galaxy.systemmonitor.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.galaxy.systemmonitor.data.model.PowerInfo

/**
 * Reads instantaneous battery current and derives power. No permission required.
 * Only polled while the battery screen is visible.
 */
class PowerReader(private val context: Context) {

    fun read(): PowerInfo {
        val bm = context.getSystemService(BatteryManager::class.java)
        fun prop(id: Int): Int = try {
            bm?.getIntProperty(id) ?: Int.MIN_VALUE
        } catch (e: Exception) {
            Int.MIN_VALUE
        }
        val voltage = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        } catch (e: Exception) {
            null
        }
        return PowerParser.parse(
            PowerParser.Raw(
                timestamp = System.currentTimeMillis(),
                currentNow = prop(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                currentAverage = prop(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
                chargeCounter = prop(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                // Same normalisation and plausibility range as BatteryParser.
                voltageMv = voltage
                    ?.let { if (it in 1..10) it * 1000 else it }
                    ?.takeIf { it in BatteryParser.PLAUSIBLE_VOLTAGE_MV },
            ),
        )
    }
}
