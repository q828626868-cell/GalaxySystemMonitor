package com.galaxy.systemmonitor.domain

/**
 * Threshold based hints for battery temperature. These are user-configurable hints, not a
 * Samsung or Android hardware diagnosis.
 */
enum class TempLevel(val label: String) {
    NORMAL("正常"),
    ELEVATED("偏高"),
    HIGH("高溫警告"),
    UNAVAILABLE("資料不可用"),
}

data class TempPoint(val timestamp: Long, val celsius: Double)

data class TempWindowStats(
    val windowMillis: Long,
    val min: Double,
    val max: Double,
    val average: Double,
    val first: Double,
    val last: Double,
    val count: Int,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
) {
    val change: Double get() = last - first
}

object TemperatureAnalyzer {

    fun classify(celsius: Double?, warnC: Int, highC: Int): TempLevel = when {
        celsius == null -> TempLevel.UNAVAILABLE
        celsius >= highC -> TempLevel.HIGH
        celsius >= warnC -> TempLevel.ELEVATED
        else -> TempLevel.NORMAL
    }

    /** Statistics for points inside the window, or null when there is no reading in it. */
    fun windowStats(points: List<TempPoint>, now: Long, windowMillis: Long): TempWindowStats? {
        val inWindow = points.filter { it.timestamp in (now - windowMillis)..now }.sortedBy { it.timestamp }
        if (inWindow.isEmpty()) return null
        return TempWindowStats(
            windowMillis = windowMillis,
            min = inWindow.minOf { it.celsius },
            max = inWindow.maxOf { it.celsius },
            average = inWindow.sumOf { it.celsius } / inWindow.size,
            first = inWindow.first().celsius,
            last = inWindow.last().celsius,
            count = inWindow.size,
            firstTimestamp = inWindow.first().timestamp,
            lastTimestamp = inWindow.last().timestamp,
        )
    }
}
