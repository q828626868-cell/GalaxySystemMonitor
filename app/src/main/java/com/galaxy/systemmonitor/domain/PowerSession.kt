package com.galaxy.systemmonitor.domain

import com.galaxy.systemmonitor.data.model.PowerInfo

/**
 * Power readings collected while the battery screen is open. Kept in memory only: current
 * changes every second, which is far too fine-grained for the sample database.
 */
data class PowerSession(
    val latest: PowerInfo? = null,
    val points: List<ChartPoint> = emptyList(),
    /** Highest net charging power seen in this session, in W. */
    val peakChargeW: Double? = null,
    /** Highest discharge power (as a positive number) seen in this session, in W. */
    val peakDischargeW: Double? = null,
) {
    fun append(info: PowerInfo, windowMillis: Long = WINDOW_MILLIS): PowerSession {
        val w = info.powerW
        val kept = points.filter { it.timestamp >= info.timestamp - windowMillis }
        return PowerSession(
            latest = info,
            points = if (w != null) kept + ChartPoint(info.timestamp, w.toFloat()) else kept,
            peakChargeW = if (w != null && w > 0) maxOf(peakChargeW ?: 0.0, w) else peakChargeW,
            peakDischargeW = if (w != null && w < 0) maxOf(peakDischargeW ?: 0.0, -w) else peakDischargeW,
        )
    }

    companion object {
        const val WINDOW_MILLIS = 10 * 60_000L
        const val POLL_MILLIS = 2_000L
    }
}
