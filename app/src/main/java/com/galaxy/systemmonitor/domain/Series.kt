package com.galaxy.systemmonitor.domain

import com.galaxy.systemmonitor.data.db.SampleEntity
import com.galaxy.systemmonitor.data.db.memoryUsedFraction
import com.galaxy.systemmonitor.data.db.pluggedIn
import com.galaxy.systemmonitor.data.db.storageFreeBytes
import com.galaxy.systemmonitor.data.db.temperatureC

data class ChartPoint(val timestamp: Long, val value: Float)

enum class TimeRange(val label: String, val millis: Long) {
    H1("1 小時", DrainCalculator.HOUR),
    H6("6 小時", 6 * DrainCalculator.HOUR),
    H24("24 小時", 24 * DrainCalculator.HOUR),
    D7("7 天", 7 * 24 * DrainCalculator.HOUR),
}

object Series {

    /**
     * Splits points into separately drawn segments wherever the time gap exceeds [maxGapMillis].
     * Missing history is shown as a break, never filled in.
     */
    fun segments(points: List<ChartPoint>, maxGapMillis: Long): List<List<ChartPoint>> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.timestamp }
        val result = mutableListOf<MutableList<ChartPoint>>(mutableListOf(sorted.first()))
        for (i in 1 until sorted.size) {
            if (sorted[i].timestamp - sorted[i - 1].timestamp > maxGapMillis) {
                result += mutableListOf<ChartPoint>()
            }
            result.last() += sorted[i]
        }
        return result
    }

    /** Gap after which the chart line breaks, based on the configured sampling interval. */
    fun gapThreshold(sampleIntervalMinutes: Int): Long =
        maxOf(60L, sampleIntervalMinutes * 3L) * DrainCalculator.MINUTE

    fun batteryPoints(samples: List<SampleEntity>): List<BatteryPoint> = samples.mapNotNull { s ->
        val level = s.battery?.level ?: return@mapNotNull null
        BatteryPoint(s.timestamp, level, s.pluggedIn)
    }

    fun tempPoints(samples: List<SampleEntity>): List<TempPoint> = samples.mapNotNull { s ->
        s.temperatureC?.let { TempPoint(s.timestamp, it) }
    }

    fun levelChart(samples: List<SampleEntity>): List<ChartPoint> = samples.mapNotNull { s ->
        s.battery?.level?.let { ChartPoint(s.timestamp, it.toFloat()) }
    }

    fun temperatureChart(samples: List<SampleEntity>): List<ChartPoint> = samples.mapNotNull { s ->
        s.temperatureC?.let { ChartPoint(s.timestamp, it.toFloat()) }
    }

    fun memoryChart(samples: List<SampleEntity>): List<ChartPoint> = samples.mapNotNull { s ->
        s.memoryUsedFraction?.let { ChartPoint(s.timestamp, (it * 100).toFloat()) }
    }

    fun storageFreeGbChart(samples: List<SampleEntity>): List<ChartPoint> = samples.mapNotNull { s ->
        s.storageFreeBytes?.let { ChartPoint(s.timestamp, (it / 1e9).toFloat()) }
    }
}
