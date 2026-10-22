package com.galaxy.systemmonitor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(sample: SampleEntity): Long

    @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): SampleEntity?

    @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<SampleEntity?>

    @Query("SELECT * FROM samples WHERE timestamp >= :from ORDER BY timestamp ASC")
    suspend fun since(from: Long): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE timestamp >= :from ORDER BY timestamp ASC")
    fun observeSince(from: Long): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SampleEntity>>

    @Query("SELECT COUNT(*) FROM samples")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(timestamp) FROM samples")
    fun observeOldestTimestamp(): Flow<Long?>

    @Query("SELECT MAX(timestamp) FROM samples WHERE source = 'SCHEDULED'")
    fun observeLastScheduled(): Flow<Long?>

    @Query(
        "SELECT timestamp, battery_temperatureDeciC AS value FROM samples " +
            "WHERE battery_temperatureDeciC IS NOT NULL ORDER BY battery_temperatureDeciC DESC, timestamp DESC LIMIT 1",
    )
    fun observeMaxTemperature(): Flow<TimedValue?>

    @Query(
        "SELECT timestamp, battery_temperatureDeciC AS value FROM samples " +
            "WHERE battery_temperatureDeciC IS NOT NULL ORDER BY battery_temperatureDeciC ASC, timestamp DESC LIMIT 1",
    )
    fun observeMinTemperature(): Flow<TimedValue?>

    @Query("DELETE FROM samples WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM samples")
    suspend fun deleteAll(): Int
}

@Dao
interface NetworkEventDao {
    @Insert
    suspend fun insert(event: NetworkEventEntity): Long

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): NetworkEventEntity?

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<NetworkEventEntity?>

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NetworkEventEntity>>

    @Query("DELETE FROM network_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM network_events")
    suspend fun deleteAll(): Int
}
