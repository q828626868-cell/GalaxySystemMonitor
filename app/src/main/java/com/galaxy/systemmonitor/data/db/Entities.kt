package com.galaxy.systemmonitor.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One monitoring sample. Each data source is a separate embedded group so a source that
 * was unavailable at sampling time is stored as NULL columns (never as 0).
 */
@Entity(tableName = "samples", indices = [Index("timestamp")])
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** [SampleSource] name: what triggered this sample. */
    val source: String,
    @Embedded(prefix = "battery_") val battery: BatteryColumns?,
    @Embedded(prefix = "memory_") val memory: MemoryColumns?,
    @Embedded(prefix = "storage_") val storage: StorageColumns?,
    @Embedded(prefix = "network_") val network: NetworkColumns?,
)

data class BatteryColumns(
    val level: Int?,
    /** Tenths of °C, avoids floating point in the database. */
    val temperatureDeciC: Int?,
    val voltageMv: Int?,
    val status: String?,
    val plugType: String?,
    val health: String?,
)

data class MemoryColumns(
    val totalBytes: Long?,
    val availableBytes: Long?,
    val lowMemory: Boolean?,
)

data class StorageColumns(
    val totalBytes: Long?,
    val freeBytes: Long?,
)

data class NetworkColumns(
    val connected: Boolean?,
    val transport: String?,
    val validated: Boolean?,
    val metered: Boolean?,
)

/** A detected change of the default network. */
@Entity(tableName = "network_events", indices = [Index("timestamp")])
data class NetworkEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val connected: Boolean,
    val transport: String,
    val validated: Boolean?,
    /** [NetworkDetection] name: CALLBACK (real-time) or SNAPSHOT (noticed at sampling time). */
    val detectedBy: String,
)

enum class SampleSource(val label: String) {
    SCHEDULED("背景排程"),
    APP_OPEN("開啟 App"),
    MANUAL("手動重新整理"),
    WIDGET("Widget"),
    BATTERY_EVENT("電池狀態變化"),
}

enum class NetworkDetection(val label: String) {
    CALLBACK("即時偵測"),
    SNAPSHOT("取樣時偵測（實際變化可能更早）"),
}

data class TimedValue(val timestamp: Long, val value: Int)
