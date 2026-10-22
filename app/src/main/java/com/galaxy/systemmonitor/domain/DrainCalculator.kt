package com.galaxy.systemmonitor.domain

import kotlin.math.max
import kotlin.math.min

data class BatteryPoint(
    val timestamp: Long,
    val level: Int,
    /** true = on external power, false = on battery, null = unknown. */
    val pluggedIn: Boolean?,
)

enum class RateStatus {
    OK,
    /** Not enough valid interval time in the window. */
    INSUFFICIENT_DATA,
    /** Only the opposite mode (e.g. charging) was observed in the window. */
    OTHER_MODE_ONLY,
}

/**
 * Average change of battery percentage per hour. This is a percentage-point rate, not
 * electrical power and not a capacity measurement.
 */
data class RateResult(
    val windowMillis: Long,
    /** Percentage points per hour (positive number for both drain and charge), null if not computable. */
    val pointsPerHour: Double?,
    val status: RateStatus,
    /** Total time covered by the intervals actually used. */
    val coveredMillis: Long,
    /** Total percentage points changed across the used intervals. */
    val totalPoints: Int,
    val intervalsUsed: Int,
    /** First/last timestamps of the intervals actually used (the real data range). */
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
)

object DrainCalculator {
    const val HOUR = 3_600_000L
    const val MINUTE = 60_000L

    /** Consecutive samples further apart than this are not paired (unseen charge cycles may hide inside). */
    const val MAX_INTERVAL_GAP = 3 * HOUR

    /** A single interval dropping/rising faster than this with >= JUMP_MIN_POINTS is treated as a sensor jump. */
    const val JUMP_POINTS_PER_HOUR = 60.0
    const val JUMP_MIN_POINTS = 5

    /** Minimum covered time needed before a rate is shown for a window. */
    fun minCoverage(windowMillis: Long): Long =
        min(4 * HOUR, max(30 * MINUTE, windowMillis / 4))

    fun discharge(points: List<BatteryPoint>, now: Long, windowMillis: Long): RateResult =
        compute(points, now, windowMillis, charging = false)

    fun charge(points: List<BatteryPoint>, now: Long, windowMillis: Long): RateResult =
        compute(points, now, windowMillis, charging = true)

    private fun compute(points: List<BatteryPoint>, now: Long, windowMillis: Long, charging: Boolean): RateResult {
        val from = now - windowMillis
        val inWindow = points.filter { it.timestamp in from..now }.sortedBy { it.timestamp }

        var covered = 0L
        var totalPoints = 0
        var used = 0
        var otherModeIntervals = 0
        var first: Long? = null
        var last: Long? = null

        for (i in 1 until inWindow.size) {
            val a = inWindow[i - 1]
            val b = inWindow[i]
            val dt = b.timestamp - a.timestamp
            if (dt <= 0 || dt > MAX_INTERVAL_GAP) continue

            val bothOnBattery = a.pluggedIn == false && b.pluggedIn == false
            val bothPlugged = a.pluggedIn == true && b.pluggedIn == true
            if (charging && bothOnBattery || !charging && bothPlugged) otherModeIntervals++

            val delta = if (charging) b.level - a.level else a.level - b.level
            val valid = if (charging) {
                // Exclude "full and topping off" periods; they are not charging speed.
                bothPlugged && delta >= 0 && a.level < 100
            } else {
                // A rise while unplugged means a charge happened that we did not see.
                bothOnBattery && delta >= 0
            }
            if (!valid) continue

            val rate = delta / (dt.toDouble() / HOUR)
            if (delta >= JUMP_MIN_POINTS && rate > JUMP_POINTS_PER_HOUR) continue

            covered += dt
            totalPoints += delta
            used++
            if (first == null) first = a.timestamp
            last = b.timestamp
        }

        val enough = used > 0 && covered >= minCoverage(windowMillis)
        val status = when {
            enough -> RateStatus.OK
            used == 0 && otherModeIntervals > 0 -> RateStatus.OTHER_MODE_ONLY
            else -> RateStatus.INSUFFICIENT_DATA
        }
        return RateResult(
            windowMillis = windowMillis,
            pointsPerHour = if (enough) totalPoints / (covered.toDouble() / HOUR) else null,
            status = status,
            coveredMillis = covered,
            totalPoints = totalPoints,
            intervalsUsed = used,
            firstTimestamp = first,
            lastTimestamp = last,
        )
    }
}
