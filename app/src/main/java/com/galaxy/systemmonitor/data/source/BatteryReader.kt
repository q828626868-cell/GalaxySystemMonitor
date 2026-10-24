package com.galaxy.systemmonitor.data.source

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.Reading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the sticky ACTION_BATTERY_CHANGED broadcast. No permission required, except for the
 * optional state-of-health value, which needs BATTERY_STATS granted via ADB.
 */
class BatteryReader(private val context: Context) {

    fun read(): Reading<BatteryInfo> = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) {
            Reading.Unavailable("系統未提供電池狀態")
        } else {
            Reading.Ok(fromIntent(intent))
        }
    } catch (e: Exception) {
        Reading.Unavailable("讀取電池資訊失敗：${e.message ?: e.javaClass.simpleName}")
    }

    /**
     * Battery broadcasts while collected. Only used while the app is visible, so it
     * never keeps the device awake on its own.
     */
    fun changes(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(fromIntent(intent))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun fromIntent(intent: Intent): BatteryInfo {
        var level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        var scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            // Fallback to BatteryManager property; returns Integer.MIN_VALUE when unsupported.
            val bm = context.getSystemService(BatteryManager::class.java)
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
            if (capacity != Int.MIN_VALUE) {
                level = capacity
                scale = 100
            }
        }
        val cycle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        } else {
            -1
        }
        val sohSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val sohPermitted = context.checkSelfPermission(Manifest.permission.BATTERY_STATS) ==
            PackageManager.PERMISSION_GRANTED
        val soh = if (sohSupported && sohPermitted) readStateOfHealth() else Int.MIN_VALUE
        return BatteryParser.parse(
            BatteryParser.Raw(
                level = level,
                scale = scale,
                temperatureTenths = if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                    intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                } else {
                    null
                },
                voltage = if (intent.hasExtra(BatteryManager.EXTRA_VOLTAGE)) {
                    intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                } else {
                    null
                },
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
                cycleCount = cycle,
                stateOfHealth = soh,
                stateOfHealthPermitted = sohPermitted,
                stateOfHealthSupported = sohSupported,
            ),
        )
    }

    /** Returns Int.MIN_VALUE when the battery HAL does not implement it or access is refused. */
    private fun readStateOfHealth(): Int = try {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BATTERY_PROPERTY_STATE_OF_HEALTH)
            ?: Int.MIN_VALUE
    } catch (e: SecurityException) {
        Int.MIN_VALUE
    }

    private companion object {
        /** BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH (@SystemApi, API 34). */
        const val BATTERY_PROPERTY_STATE_OF_HEALTH = 10
    }
}
