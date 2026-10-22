package com.galaxy.systemmonitor.data.db

import com.galaxy.systemmonitor.data.model.ChargeStatus
import com.galaxy.systemmonitor.data.model.NetworkInfo
import com.galaxy.systemmonitor.data.model.PlugType
import com.galaxy.systemmonitor.data.model.SystemSnapshot
import com.galaxy.systemmonitor.data.model.valueOrNull
import kotlin.math.roundToInt

object SampleMapper {

    fun toEntity(snapshot: SystemSnapshot, source: SampleSource): SampleEntity {
        val b = snapshot.battery.valueOrNull()
        val m = snapshot.memory.valueOrNull()
        val s = snapshot.storage.valueOrNull()
        val n = snapshot.network.valueOrNull()
        return SampleEntity(
            timestamp = snapshot.timestamp,
            source = source.name,
            battery = b?.let {
                BatteryColumns(
                    level = it.levelPercent,
                    temperatureDeciC = it.temperatureC?.let { c -> (c * 10).roundToInt() },
                    voltageMv = it.voltageMv,
                    status = it.status.name,
                    plugType = it.plugType.name,
                    health = it.health.name,
                )
            },
            memory = m?.let { MemoryColumns(it.totalBytes, it.availableBytes, it.lowMemory) },
            storage = s?.let { StorageColumns(it.totalBytes, it.freeBytes) },
            network = n?.let { NetworkColumns(it.connected, it.transport.name, it.validated, it.metered) },
        )
    }

    /** True when two samples carry the same user-visible state (used to skip redundant writes). */
    fun sameState(a: SampleEntity, b: SampleEntity): Boolean =
        a.battery?.level == b.battery?.level &&
            a.battery?.plugType == b.battery?.plugType &&
            a.battery?.status == b.battery?.status &&
            a.network?.connected == b.network?.connected &&
            a.network?.transport == b.network?.transport

    fun networkEventKey(info: NetworkInfo): String =
        "${info.connected}|${info.transport.name}|${info.validated}"

    fun networkEventKey(event: NetworkEventEntity): String =
        "${event.connected}|${event.transport}|${event.validated}"
}

val SampleEntity.temperatureC: Double? get() = battery?.temperatureDeciC?.let { it / 10.0 }

val SampleEntity.pluggedIn: Boolean?
    get() = when (battery?.plugType) {
        null, PlugType.UNKNOWN.name -> null
        PlugType.NONE.name -> false
        else -> true
    }

val SampleEntity.chargeStatus: ChargeStatus
    get() = ChargeStatus.entries.firstOrNull { it.name == battery?.status } ?: ChargeStatus.UNKNOWN

val SampleEntity.memoryUsedFraction: Double?
    get() {
        val total = memory?.totalBytes ?: return null
        val avail = memory.availableBytes ?: return null
        if (total <= 0) return null
        return ((total - avail).coerceAtLeast(0)).toDouble() / total
    }

val SampleEntity.storageFreeBytes: Long? get() = storage?.freeBytes
