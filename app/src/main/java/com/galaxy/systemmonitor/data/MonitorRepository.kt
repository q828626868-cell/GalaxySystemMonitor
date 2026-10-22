package com.galaxy.systemmonitor.data

import androidx.room.withTransaction
import com.galaxy.systemmonitor.data.db.AppDatabase
import com.galaxy.systemmonitor.data.db.NetworkDetection
import com.galaxy.systemmonitor.data.db.NetworkEventEntity
import com.galaxy.systemmonitor.data.db.SampleEntity
import com.galaxy.systemmonitor.data.db.SampleMapper
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.data.db.TimedValue
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.NetworkInfo
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.data.model.SystemSnapshot
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.data.source.BatteryReader
import com.galaxy.systemmonitor.data.source.MemoryReader
import com.galaxy.systemmonitor.data.source.NetworkReader
import com.galaxy.systemmonitor.data.source.StorageReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class StoreResult { STORED, SKIPPED, FAILED }

data class CaptureOutcome(val snapshot: SystemSnapshot, val store: StoreResult)

/**
 * Single entry point for reading live system state and for the local history database.
 * All data stays in the app's private database; nothing is sent anywhere.
 */
class MonitorRepository(
    private val db: AppDatabase,
    private val batteryReader: BatteryReader,
    private val memoryReader: MemoryReader,
    private val storageReader: StorageReader,
    private val networkReader: NetworkReader,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sampleDao = db.sampleDao()
    private val eventDao = db.networkEventDao()

    private val _live = MutableStateFlow<SystemSnapshot?>(null)
    /** Most recent in-memory snapshot (not necessarily stored). */
    val live: StateFlow<SystemSnapshot?> = _live.asStateFlow()

    private val _databaseError = MutableStateFlow<String?>(null)
    val databaseError: StateFlow<String?> = _databaseError.asStateFlow()

    private val writeMutex = Mutex()
    private var lastStored: SampleEntity? = null
    private var lastNetworkKey: String? = null

    /** Live battery broadcasts; collect only while UI is visible. */
    fun batteryChanges(): Flow<BatteryInfo> = batteryReader.changes()

    fun hasPhonePermission() = networkReader.hasPhonePermission()

    suspend fun capture(): SystemSnapshot = withContext(Dispatchers.IO) {
        SystemSnapshot(
            timestamp = clock(),
            battery = batteryReader.read(),
            memory = memoryReader.read(),
            storage = storageReader.read(),
            network = networkReader.read(),
        ).also { _live.value = it }
    }

    suspend fun captureAndStore(
        source: SampleSource,
        minSpacingMillis: Long = DEFAULT_MIN_SPACING,
    ): CaptureOutcome {
        val snapshot = capture()
        val result = store(snapshot, source, minSpacingMillis)
        snapshot.network.valueOrNull()?.let {
            recordNetworkIfChanged(it, NetworkDetection.SNAPSHOT, snapshot.timestamp)
        }
        return CaptureOutcome(snapshot, result)
    }

    /**
     * Inserts a sample unless an identical state was stored within [minSpacingMillis]
     * (avoids redundant writes from repeated refreshes).
     */
    suspend fun store(snapshot: SystemSnapshot, source: SampleSource, minSpacingMillis: Long): StoreResult =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val entity = SampleMapper.toEntity(snapshot, source)
                    val last = lastStored ?: sampleDao.latest()
                    val age = entity.timestamp - (last?.timestamp ?: Long.MIN_VALUE / 2)
                    if (last != null && age in 0 until minSpacingMillis && SampleMapper.sameState(last, entity)) {
                        lastStored = last
                        return@withLock StoreResult.SKIPPED
                    }
                    val id = sampleDao.insert(entity)
                    lastStored = entity.copy(id = id)
                    _databaseError.value = null
                    StoreResult.STORED
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _databaseError.value = "歷史紀錄寫入失敗：${e.message ?: e.javaClass.simpleName}"
                    StoreResult.FAILED
                }
            }
        }

    /**
     * Called for live battery broadcasts while the app is visible. Updates the in-memory
     * snapshot and stores a full sample only when the level or power state changed,
     * or the last stored sample is older than [BATTERY_EVENT_REFRESH].
     */
    suspend fun onBatteryBroadcast(info: BatteryInfo): Boolean {
        _live.update { it?.copy(battery = Reading.Ok(info), timestamp = clock()) }
        val last = writeMutex.withLock { lastStored ?: safeLatest() }
        val changed = last == null ||
            last.battery?.level != info.levelPercent ||
            last.battery?.plugType != info.plugType.name ||
            last.battery?.status != info.status.name ||
            clock() - last.timestamp > BATTERY_EVENT_REFRESH
        if (!changed) return false
        return captureAndStore(SampleSource.BATTERY_EVENT, minSpacingMillis = 0).store == StoreResult.STORED
    }

    fun updateLiveNetwork(info: Reading<NetworkInfo>) {
        _live.update { it?.copy(network = info) }
    }

    suspend fun readNetwork(): Reading<NetworkInfo> = withContext(Dispatchers.IO) { networkReader.read() }

    suspend fun recordNetworkIfChanged(info: NetworkInfo, detectedBy: NetworkDetection, timestamp: Long = clock()) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val key = SampleMapper.networkEventKey(info)
                    val previous = lastNetworkKey ?: eventDao.latest()?.let { SampleMapper.networkEventKey(it) }
                    if (previous != key) {
                        eventDao.insert(
                            NetworkEventEntity(
                                timestamp = timestamp,
                                connected = info.connected,
                                transport = info.transport.name,
                                validated = info.validated,
                                detectedBy = detectedBy.name,
                            ),
                        )
                    }
                    lastNetworkKey = key
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _databaseError.value = "網路事件寫入失敗：${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    /** Deletes this app's own history older than [retentionDays]. Touches nothing else. */
    suspend fun prune(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val cutoff = clock() - retentionDays * DAY
                val removed = sampleDao.deleteOlderThan(cutoff) + eventDao.deleteOlderThan(cutoff)
                if (lastStored?.let { it.timestamp < cutoff } == true) lastStored = null
                removed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _databaseError.value = "清理過期紀錄失敗：${e.message ?: e.javaClass.simpleName}"
                0
            }
        }
    }

    /** Deletes every row of this app's own monitoring history (samples + network events). */
    suspend fun clearHistory(): Result<Int> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val removed = db.withTransaction { sampleDao.deleteAll() + eventDao.deleteAll() }
                lastStored = null
                lastNetworkKey = null
                Result.success(removed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun safeLatest(): SampleEntity? = try {
        sampleDao.latest()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun latestSample(): SampleEntity? = withContext(Dispatchers.IO) { safeLatest() }

    suspend fun samplesSince(from: Long): List<SampleEntity> = withContext(Dispatchers.IO) { sampleDao.since(from) }

    private fun <T> Flow<T>.reportErrors(fallback: T): Flow<T> = catch { e ->
        _databaseError.value = "讀取歷史紀錄失敗：${e.message ?: e.javaClass.simpleName}"
        emit(fallback)
    }

    fun observeSince(from: Long): Flow<List<SampleEntity>> = sampleDao.observeSince(from).reportErrors(emptyList())
    fun observeLatest(): Flow<SampleEntity?> = sampleDao.observeLatest().reportErrors(null)
    fun observeRecent(limit: Int): Flow<List<SampleEntity>> = sampleDao.observeRecent(limit).reportErrors(emptyList())
    fun observeCount(): Flow<Int> = sampleDao.observeCount().reportErrors(0)
    fun observeOldestTimestamp(): Flow<Long?> = sampleDao.observeOldestTimestamp().reportErrors(null)
    fun observeLastScheduled(): Flow<Long?> = sampleDao.observeLastScheduled().reportErrors(null)
    fun observeMaxTemperature(): Flow<TimedValue?> = sampleDao.observeMaxTemperature().reportErrors(null)
    fun observeMinTemperature(): Flow<TimedValue?> = sampleDao.observeMinTemperature().reportErrors(null)
    fun observeLatestNetworkEvent(): Flow<NetworkEventEntity?> = eventDao.observeLatest().reportErrors(null)
    fun observeRecentNetworkEvents(limit: Int): Flow<List<NetworkEventEntity>> =
        eventDao.observeRecent(limit).reportErrors(emptyList())

    companion object {
        const val DAY = 86_400_000L
        const val DEFAULT_MIN_SPACING = 60_000L
        const val BATTERY_EVENT_REFRESH = 10 * 60_000L
    }
}
