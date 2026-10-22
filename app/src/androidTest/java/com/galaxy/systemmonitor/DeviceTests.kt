package com.galaxy.systemmonitor

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxy.systemmonitor.data.MonitorRepository
import com.galaxy.systemmonitor.data.StoreResult
import com.galaxy.systemmonitor.data.db.AppDatabase
import com.galaxy.systemmonitor.data.db.BatteryColumns
import com.galaxy.systemmonitor.data.db.SampleEntity
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.data.source.BatteryReader
import com.galaxy.systemmonitor.data.source.MemoryReader
import com.galaxy.systemmonitor.data.source.NetworkReader
import com.galaxy.systemmonitor.data.source.StorageReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Reads the real system APIs on the connected device / emulator. */
@RunWith(AndroidJUnit4::class)
class SystemReadersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun batteryIsReadable() {
        val r = BatteryReader(context).read()
        assertTrue("battery: $r", r is Reading.Ok)
        val b = (r as Reading.Ok).value
        assertNotNull(b.levelPercent)
        assertTrue(b.levelPercent!! in 0..100)
        b.temperatureC?.let { assertTrue(it in -30.0..90.0) }
    }

    @Test
    fun memoryIsReadable() {
        val r = MemoryReader(context).read()
        assertTrue("memory: $r", r is Reading.Ok)
        val m = (r as Reading.Ok).value
        assertTrue(m.totalBytes > 0)
        assertTrue(m.availableBytes in 0..m.totalBytes)
    }

    @Test
    fun storageIsReadable() {
        val r = StorageReader(context).read()
        assertTrue("storage: $r", r is Reading.Ok)
        val s = (r as Reading.Ok).value
        assertTrue(s.totalBytes > 0)
        assertTrue(s.freeBytes in 0..s.totalBytes)
    }

    @Test
    fun networkIsReadable() = runBlocking {
        val r = NetworkReader(context).read()
        assertTrue("network: $r", r is Reading.Ok)
    }

    @Test
    fun destinationWhitelist() {
        val ok = Destinations.intent(context, Destinations.TEMPERATURE)
        assertEquals(Destinations.TEMPERATURE, Destinations.fromIntent(ok))
        val bad = Intent().putExtra("com.galaxy.systemmonitor.extra.DESTINATION", "../evil")
        assertNull(Destinations.fromIntent(bad))
        assertNull(Destinations.fromIntent(null))
    }
}

@RunWith(AndroidJUnit4::class)
class DatabaseAndRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: MonitorRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = MonitorRepository(db, BatteryReader(context), MemoryReader(context), StorageReader(context), NetworkReader(context))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nullColumnsStayNull() = runBlocking {
        val dao = db.sampleDao()
        dao.insert(SampleEntity(timestamp = 1, source = "TEST", battery = null, memory = null, storage = null, network = null))
        val s = dao.latest()!!
        assertNull(s.battery)
        assertNull(s.memory)
        dao.insert(
            SampleEntity(
                timestamp = 2, source = "TEST",
                battery = BatteryColumns(50, null, null, "DISCHARGING", "NONE", "GOOD"),
                memory = null, storage = null, network = null,
            ),
        )
        val s2 = dao.latest()!!
        assertEquals(50, s2.battery!!.level)
        assertNull(s2.battery!!.temperatureDeciC)
    }

    @Test
    fun captureStoreSkipPruneAndClear() = runBlocking {
        val first = repo.captureAndStore(SampleSource.MANUAL, minSpacingMillis = 0)
        assertEquals(StoreResult.STORED, first.store)
        // Identical state within the spacing window is not written again.
        val second = repo.captureAndStore(SampleSource.APP_OPEN, minSpacingMillis = 60_000)
        assertEquals(StoreResult.SKIPPED, second.store)
        assertEquals(1, db.sampleDao().observeCount().first())

        val stored = db.sampleDao().latest()!!
        assertNotNull(stored.battery?.level)
        assertNotNull(stored.memory?.totalBytes)
        assertNotNull(stored.storage?.totalBytes)
        assertNotNull(db.networkEventDao().latest())

        // Pruning keeps recent rows, removes old ones.
        db.sampleDao().insert(stored.copy(id = 0, timestamp = System.currentTimeMillis() - 40 * MonitorRepository.DAY))
        assertEquals(2, db.sampleDao().observeCount().first())
        assertEquals(1, repo.prune(30))
        assertEquals(1, db.sampleDao().observeCount().first())

        val cleared = repo.clearHistory()
        assertTrue(cleared.isSuccess)
        assertEquals(0, db.sampleDao().observeCount().first())
        assertNull(db.networkEventDao().latest())

        // After clearing, the next capture is stored again.
        assertEquals(StoreResult.STORED, repo.captureAndStore(SampleSource.MANUAL, 60_000).store)
    }
}
