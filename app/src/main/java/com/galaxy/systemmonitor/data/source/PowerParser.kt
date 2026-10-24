package com.galaxy.systemmonitor.data.source

import com.galaxy.systemmonitor.data.model.PowerInfo
import kotlin.math.abs

/**
 * Converts raw BatteryManager current / charge-counter properties into [PowerInfo].
 * The documented unit is microamperes, but some vendors report milliamperes; the unit is
 * inferred from magnitude. Pure Kotlin so it can be unit tested on the JVM.
 */
object PowerParser {

    data class Raw(
        val timestamp: Long,
        /** BATTERY_PROPERTY_CURRENT_NOW, Int.MIN_VALUE when unsupported. */
        val currentNow: Int,
        /** BATTERY_PROPERTY_CURRENT_AVERAGE, Int.MIN_VALUE when unsupported. */
        val currentAverage: Int,
        /** BATTERY_PROPERTY_CHARGE_COUNTER (µAh), Int.MIN_VALUE when unsupported. */
        val chargeCounter: Int,
        /** Already validated battery voltage in mV, null when unavailable. */
        val voltageMv: Int?,
    )

    /**
     * Below this magnitude the value is taken as mA. A phone with the screen on never draws less
     * than 20 mA, and no phone battery carries 20 A, so the two ranges do not overlap in practice.
     */
    private const val MICROAMP_THRESHOLD = 20_000
    /** Accepted battery current magnitude in mA. */
    const val MAX_PLAUSIBLE_MA = 15_000
    /** Accepted charge counter range in mAh. */
    val PLAUSIBLE_CHARGE_MAH = 1..20_000

    fun parse(raw: Raw): PowerInfo {
        val notes = mutableListOf<String>()

        val currentMa = toMilliamps(raw.currentNow)
        if (raw.currentNow == Int.MIN_VALUE) {
            notes += "裝置未提供即時電流"
        } else if (currentMa == null) {
            notes += "系統回報的電流數值異常（${raw.currentNow}），已忽略"
        }
        val averageMa = toMilliamps(raw.currentAverage)

        val chargeMah = raw.chargeCounter.takeIf { it != Int.MIN_VALUE }
            ?.let { if (abs(it) >= 100_000) it / 1000 else it }
            ?.takeIf { it in PLAUSIBLE_CHARGE_MAH }

        val powerW = if (currentMa != null && raw.voltageMv != null) {
            currentMa * raw.voltageMv / 1_000_000.0
        } else {
            null
        }

        return PowerInfo(
            timestamp = raw.timestamp,
            currentMa = currentMa,
            averageCurrentMa = averageMa,
            voltageMv = raw.voltageMv,
            powerW = powerW,
            chargeCounterMah = chargeMah,
            notes = notes,
        )
    }

    private fun toMilliamps(value: Int): Int? {
        if (value == Int.MIN_VALUE) return null
        val ma = if (abs(value) >= MICROAMP_THRESHOLD) value / 1000 else value
        return ma.takeIf { abs(it) <= MAX_PLAUSIBLE_MA }
    }
}
