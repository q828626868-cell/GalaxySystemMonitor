package com.galaxy.systemmonitor.util

import com.galaxy.systemmonitor.domain.RateResult
import com.galaxy.systemmonitor.domain.RateStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    const val UNAVAILABLE = "不可用"
    const val INSUFFICIENT = "資料不足"

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    /** Decimal units (1 GB = 10^9 bytes), matching Android's Settings app. */
    fun bytes(value: Long): String = when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", value / 1e9)
        value >= 1_000_000L -> String.format(Locale.US, "%.0f MB", value / 1e6)
        else -> String.format(Locale.US, "%.0f KB", value / 1e3)
    }

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    fun temperature(celsius: Double?): String =
        celsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: UNAVAILABLE

    fun voltage(mv: Int?): String =
        mv?.let { String.format(Locale.US, "%.2f V", it / 1000.0) } ?: UNAVAILABLE

    fun time(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFmt.format(Instant.ofEpochMilli(ts).atZone(zone))

    /** "HH:mm" for today, "MM/dd HH:mm" otherwise. */
    fun dateTime(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val z = Instant.ofEpochMilli(ts).atZone(zone)
        return if (z.toLocalDate() == LocalDate.now(zone)) timeFmt.format(z) else dateTimeFmt.format(z)
    }

    fun fullDateTime(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFmt.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun relative(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ts
        return when {
            diff < 0 -> dateTime(ts)
            diff < 60_000 -> "剛剛"
            diff < 3_600_000 -> "${diff / 60_000} 分鐘前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小時前"
            else -> "${diff / 86_400_000} 天前"
        }
    }

    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return buildString {
            if (days > 0) append("$days 天 ")
            if (hours > 0) append("$hours 小時 ")
            if (days == 0L && (minutes > 0 || hours == 0L)) append("$minutes 分")
        }.trim()
    }

    /** Compact rate for widgets / cards, e.g. "1.8%/h". */
    fun rateShort(result: RateResult?, charging: Boolean = false): String = when {
        result == null -> UNAVAILABLE
        result.status == RateStatus.OK -> String.format(Locale.US, "%.1f%%/h", result.pointsPerHour)
        result.status == RateStatus.OTHER_MODE_ONLY -> if (charging) "未充電" else "充電中"
        else -> INSUFFICIENT
    }

    fun rateLong(result: RateResult, charging: Boolean = false): String = when (result.status) {
        RateStatus.OK -> String.format(Locale.US, "%.1f 個百分點／小時", result.pointsPerHour)
        RateStatus.OTHER_MODE_ONLY -> if (charging) "此區間未充電" else "此區間主要在充電"
        RateStatus.INSUFFICIENT_DATA -> INSUFFICIENT
    }
}
