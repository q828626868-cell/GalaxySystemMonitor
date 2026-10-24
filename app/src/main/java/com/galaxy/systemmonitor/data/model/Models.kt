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
    /**
     * Battery state of health in percent (BATTERY_PROPERTY_STATE_OF_HEALTH, Android 14+).
     * Requires BATTERY_STATS, which can only be granted via ADB.
     */
    val stateOfHealth: Reading<Int> = Reading.Unavailable("裝置未提供"),
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

data class PowerInfo(
    val timestamp: Long,
    /** Battery current in mA; positive = flowing into the battery (Android convention). */
    val currentMa: Int?,
    /** Vendor-averaged current in mA, null when not provided. */
    val averageCurrentMa: Int?,
    val voltageMv: Int?,
    /** Net battery power in W; positive = charging the battery, negative = drawing from it. */
    val powerW: Double?,
    /** Remaining charge reported by the fuel gauge, in mAh. */
    val chargeCounterMah: Int?,
    val notes: List<String> = emptyList(),
)

/** Foreground (visible) time of one app within a queried range. */
data class AppScreenTime(val packageName: String, val label: String, val millis: Long)

/** Network bytes of one UID within a queried range. */
data class AppTraffic(
    val uid: Int,
    val label: String,
    /** Representative package for the icon, null for special UIDs (removed apps, tethering…). */
    val packageName: String?,
    val wifiBytes: Long,
    val mobileBytes: Long,
    /** Bytes transferred while the app was not in the foreground. */
    val backgroundBytes: Long,
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

/** Device facts that do not change while the app runs. Null fields mean "not provided by this device". */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val marketName: String?,
    /** User-set device name from Settings > About phone. */
    val deviceName: String?,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val oneUiVersion: String?,
    val buildNumber: String,
    val baseband: String?,
    val kernel: String?,
    val abis: List<String>,
    val socManufacturer: String?,
    val socModel: String?,
    val socName: String?,
    val coreCount: Int,
    val clusters: List<com.galaxy.systemmonitor.domain.CpuTopology.Cluster>,
    val gpuRenderer: String?,
    val gpuVendor: String?,
    val openGlVersion: String?,
    val vulkanVersion: String?,
    val display: DisplayInfo?,
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val currentRefreshHz: Float,
    val supportedRefreshHz: List<Float>,
    val hdrTypes: List<String>,
)

/** Values polled while the device screen is open. */
data class DeviceLive(
    /** Current frequency per core in MHz; null entries are offline or unreadable cores. */
    val coreMhz: Map<Int, Int?>,
    /** false when the system blocks reading current frequencies (common on Android 8+). */
    val coreFrequencyReadable: Boolean,
    val thermalStatus: ThermalStatus,
    /** 0.0 = no throttling, 1.0 = severe throttling threshold; null when unsupported. */
    val thermalHeadroom: Float?,
    val elapsedRealtimeMillis: Long,
    /** Time the CPU was awake since boot. */
    val awakeMillis: Long,
) {
    /** Share of time since boot spent in deep sleep. */
    val deepSleepFraction: Double
        get() = if (elapsedRealtimeMillis > 0) {
            ((elapsedRealtimeMillis - awakeMillis).toDouble() / elapsedRealtimeMillis).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}

enum class ThermalStatus(val label: String) {
    NONE("正常"),
    LIGHT("輕微發熱"),
    MODERATE("中度發熱，可能開始降頻"),
    SEVERE("嚴重發熱，效能受限"),
    CRITICAL("危急"),
    EMERGENCY("緊急"),
    SHUTDOWN("即將關機"),
    UNKNOWN("不明"),
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
