package com.galaxy.systemmonitor.data.model

/**
 * Result of reading one data source. A source that cannot be read is reported as
 * [Unavailable] with a reason; it is never converted into a zero value.
 */
sealed interface Reading<out T> {
    data class Ok<T>(val value: T) : Reading<T>
    data class Unavailable(val reason: String) : Reading<Nothing>
}

fun <T> Reading<T>.valueOrNull(): T? = (this as? Reading.Ok<T>)?.value

fun Reading<*>.unavailableReason(): String? = (this as? Reading.Unavailable)?.reason

enum class ChargeStatus(val label: String) {
    CHARGING("充電中"),
    DISCHARGING("放電中"),
    FULL("已充滿"),
    NOT_CHARGING("未充電"),
    UNKNOWN("未知"),
}

enum class PlugType(val label: String) {
    NONE("未接電源"),
    AC("AC 充電器"),
    USB("USB"),
    WIRELESS("無線充電"),
    DOCK("充電座"),
    UNKNOWN("未知"),
}

/** Android's coarse BATTERY_HEALTH_* value. This is NOT a capacity percentage. */
enum class BatteryHealth(val label: String) {
    GOOD("良好"),
    OVERHEAT("過熱"),
    DEAD("損壞"),
    OVER_VOLTAGE("電壓過高"),
    FAILURE("不明故障"),
    COLD("溫度過低"),
    UNKNOWN("未知"),
}

data class BatteryInfo(
    /** 0..100, or null when the system did not report a usable value. */
    val levelPercent: Int?,
    /** Battery (not CPU) temperature in °C, or null when unavailable / rejected as abnormal. */
    val temperatureC: Double?,
    val voltageMv: Int?,
    val status: ChargeStatus,
    val plugType: PlugType,
    val health: BatteryHealth,
    val technology: String?,
    /** Charge cycle count from Android 14+ (EXTRA_CYCLE_COUNT) when the device reports it. */
    val cycleCount: Int?,
    /** Human readable notes about rejected or suspicious values. */
    val notes: List<String> = emptyList(),
) {
    /** true = on external power, false = on battery, null = unknown. */
    val pluggedIn: Boolean?
        get() = when (plugType) {
            PlugType.NONE -> false
            PlugType.UNKNOWN -> null
            else -> true
        }
}

data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryThresholdBytes: Long?,
    val lowMemory: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Double get() = if (totalBytes > 0) usedBytes.toDouble() / totalBytes else 0.0
}

enum class StorageSource(val label: String) {
    STORAGE_STATS("StorageStatsManager（與系統設定一致）"),
    STAT_FS("StatFs（/data 分割區）"),
}

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val source: StorageSource,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Double get() = if (totalBytes > 0) usedBytes.toDouble() / totalBytes else 0.0
    val freeFraction: Double get() = if (totalBytes > 0) freeBytes.toDouble() / totalBytes else 0.0
}

enum class NetworkTransport(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("行動網路"),
    ETHERNET("乙太網路"),
    BLUETOOTH("藍牙網路"),
    VPN("VPN"),
    OTHER("其他"),
    NONE("無連線"),
    UNKNOWN("未知");

    companion object {
        fun fromName(name: String?): NetworkTransport =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

enum class CellularStatus { NOT_CELLULAR, PERMISSION_REQUIRED, UNKNOWN, KNOWN }

data class CellularGeneration(val status: CellularStatus, val label: String? = null) {
    val displayText: String
        get() = when (status) {
            CellularStatus.NOT_CELLULAR -> "不適用"
            CellularStatus.PERMISSION_REQUIRED -> "權限不足（選用功能）"
            CellularStatus.UNKNOWN -> "未知"
            CellularStatus.KNOWN -> label ?: "未知"
        }
}

data class NetworkInfo(
    /** A default network exists. Says nothing about whether the internet is reachable. */
    val connected: Boolean,
    val transport: NetworkTransport,
    val viaVpn: Boolean,
    /** Network advertises NET_CAPABILITY_INTERNET (null when not connected). */
    val hasInternetCapability: Boolean?,
    /** System validated internet access (NET_CAPABILITY_VALIDATED). null when not connected. */
    val validated: Boolean?,
    val metered: Boolean?,
    val cellular: CellularGeneration,
)

data class SystemSnapshot(
    val timestamp: Long,
    val battery: Reading<BatteryInfo>,
    val memory: Reading<MemoryInfo>,
    val storage: Reading<StorageInfo>,
    val network: Reading<NetworkInfo>,
)
