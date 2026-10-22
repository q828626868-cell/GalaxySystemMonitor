package com.galaxy.systemmonitor.data.source

import com.galaxy.systemmonitor.data.model.BatteryHealth
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.ChargeStatus
import com.galaxy.systemmonitor.data.model.PlugType
import kotlin.math.roundToInt

/**
 * Converts raw BatteryManager extras into [BatteryInfo], rejecting values that are
 * missing or physically implausible instead of passing them through as numbers.
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object BatteryParser {

    data class Raw(
        val level: Int,
        val scale: Int,
        /** EXTRA_TEMPERATURE in tenths of °C, null when the extra is absent. */
        val temperatureTenths: Int?,
        /** EXTRA_VOLTAGE (normally mV), null when absent. */
        val voltage: Int?,
        val status: Int,
        val plugged: Int,
        val health: Int,
        val technology: String?,
        val cycleCount: Int,
    )

    // Values mirror android.os.BatteryManager constants.
    private const val STATUS_CHARGING = 2
    private const val STATUS_DISCHARGING = 3
    private const val STATUS_NOT_CHARGING = 4
    private const val STATUS_FULL = 5
    private const val PLUGGED_AC = 1
    private const val PLUGGED_USB = 2
    private const val PLUGGED_WIRELESS = 4
    private const val PLUGGED_DOCK = 8
    private const val HEALTH_GOOD = 2
    private const val HEALTH_OVERHEAT = 3
    private const val HEALTH_DEAD = 4
    private const val HEALTH_OVER_VOLTAGE = 5
    private const val HEALTH_FAILURE = 6
    private const val HEALTH_COLD = 7

    /** Accepted battery temperature range in °C. Outside this the reading is treated as invalid. */
    val PLAUSIBLE_TEMPERATURE_C = -30.0..90.0
    val PLAUSIBLE_VOLTAGE_MV = 2_000..6_000

    fun parse(raw: Raw): BatteryInfo {
        val notes = mutableListOf<String>()

        val level = if (raw.level >= 0 && raw.scale > 0) {
            (raw.level * 100.0 / raw.scale).roundToInt().coerceIn(0, 100)
        } else {
            notes += "系統未回報有效的電量百分比"
            null
        }

        val temperature = when (val t = raw.temperatureTenths) {
            null, Int.MIN_VALUE -> {
                notes += "系統未提供電池溫度"
                null
            }
            0 -> {
                // Many devices without a sensor report exactly 0; treat it as "not provided".
                notes += "系統回報溫度為 0（通常代表不支援），已視為不可用"
                null
            }
            else -> {
                val c = t / 10.0
                if (c in PLAUSIBLE_TEMPERATURE_C) {
                    c
                } else {
                    notes += "系統回報的溫度數值異常（$c °C），已忽略"
                    null
                }
            }
        }

        val voltage = raw.voltage?.let { v ->
            // A few devices report volts instead of millivolts.
            val mv = if (v in 1..10) v * 1000 else v
            if (mv in PLAUSIBLE_VOLTAGE_MV) {
                mv
            } else {
                notes += if (v <= 0) "裝置未提供電壓" else "系統回報的電壓數值異常（$v），已忽略"
                null
            }
        } ?: run {
            notes += "裝置未提供電壓"
            null
        }

        val status = when (raw.status) {
            STATUS_CHARGING -> ChargeStatus.CHARGING
            STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            STATUS_FULL -> ChargeStatus.FULL
            else -> ChargeStatus.UNKNOWN
        }

        val plug = when {
            raw.plugged < 0 -> PlugType.UNKNOWN
            raw.plugged == 0 -> PlugType.NONE
            raw.plugged and PLUGGED_AC != 0 -> PlugType.AC
            raw.plugged and PLUGGED_USB != 0 -> PlugType.USB
            raw.plugged and PLUGGED_WIRELESS != 0 -> PlugType.WIRELESS
            raw.plugged and PLUGGED_DOCK != 0 -> PlugType.DOCK
            else -> PlugType.UNKNOWN
        }

        val health = when (raw.health) {
            HEALTH_GOOD -> BatteryHealth.GOOD
            HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            HEALTH_DEAD -> BatteryHealth.DEAD
            HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            HEALTH_FAILURE -> BatteryHealth.FAILURE
            HEALTH_COLD -> BatteryHealth.COLD
            else -> BatteryHealth.UNKNOWN
        }

        return BatteryInfo(
            levelPercent = level,
            temperatureC = temperature,
            voltageMv = voltage,
            status = status,
            plugType = plug,
            health = health,
            technology = raw.technology?.takeIf { it.isNotBlank() },
            cycleCount = raw.cycleCount.takeIf { it >= 0 },
            notes = notes,
        )
    }
}
