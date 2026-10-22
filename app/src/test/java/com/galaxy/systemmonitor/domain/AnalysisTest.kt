package com.galaxy.systemmonitor.domain

import com.galaxy.systemmonitor.data.db.BatteryColumns
import com.galaxy.systemmonitor.data.db.NetworkColumns
import com.galaxy.systemmonitor.data.db.SampleEntity
import com.galaxy.systemmonitor.data.db.SampleMapper
import com.galaxy.systemmonitor.data.settings.AppSettings
import com.galaxy.systemmonitor.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureAnalyzerTest {
    @Test
    fun `classification uses thresholds`() {
        assertEquals(TempLevel.NORMAL, TemperatureAnalyzer.classify(32.0, 40, 45))
        assertEquals(TempLevel.ELEVATED, TemperatureAnalyzer.classify(40.0, 40, 45))
        assertEquals(TempLevel.HIGH, TemperatureAnalyzer.classify(45.5, 40, 45))
        assertEquals(TempLevel.UNAVAILABLE, TemperatureAnalyzer.classify(null, 40, 45))
    }

    @Test
    fun `window stats only use points inside window`() {
        val now = 10_000_000L
        val pts = listOf(
            TempPoint(now - 7_200_000, 50.0),
            TempPoint(now - 3_000_000, 30.0),
            TempPoint(now - 1_000_000, 34.0),
        )
        val s = TemperatureAnalyzer.windowStats(pts, now, 3_600_000)!!
        assertEquals(2, s.count)
        assertEquals(30.0, s.min, 1e-9)
        assertEquals(34.0, s.max, 1e-9)
        assertEquals(4.0, s.change, 1e-9)
        assertNull(TemperatureAnalyzer.windowStats(pts, now, 60_000))
    }
}

class SeriesTest {
    @Test
    fun `segments break on gaps and never add points`() {
        val pts = listOf(0L, 10L, 20L, 100L, 110L, 300L).map { ChartPoint(it, 1f) }
        val segs = Series.segments(pts, 30)
        assertEquals(listOf(3, 2, 1), segs.map { it.size })
        assertEquals(pts.size, segs.sumOf { it.size })
    }

    @Test
    fun `gap threshold is at least one hour`() {
        assertEquals(3_600_000L, Series.gapThreshold(15))
        assertEquals(3 * 3_600_000L, Series.gapThreshold(60))
    }

    @Test
    fun `charts skip null columns instead of using zero`() {
        val samples = listOf(
            SampleEntity(timestamp = 1, source = "X", battery = null, memory = null, storage = null, network = null),
            SampleEntity(
                timestamp = 2, source = "X",
                battery = BatteryColumns(55, null, null, "DISCHARGING", "NONE", "GOOD"),
                memory = null, storage = null, network = null,
            ),
        )
        assertEquals(listOf(ChartPoint(2, 55f)), Series.levelChart(samples))
        assertTrue(Series.temperatureChart(samples).isEmpty())
        assertTrue(Series.memoryChart(samples).isEmpty())
    }
}

class SettingsAndMapperTest {
    @Test
    fun `sanitized settings enforce valid ranges`() {
        val s = AppSettings(retentionDays = 3, tempWarnC = 60, tempHighC = 20, lowStoragePercent = 7, sampleIntervalMinutes = 1)
            .sanitized()
        assertEquals(30, s.retentionDays)
        assertEquals(50, s.tempWarnC)
        assertTrue(s.tempHighC > s.tempWarnC)
        assertEquals(10, s.lowStoragePercent)
        assertEquals(15, s.sampleIntervalMinutes)
        assertEquals(30, AppSettings().retentionDays)
    }

    @Test
    fun `same state detection`() {
        fun e(level: Int, plug: String, net: String) = SampleEntity(
            timestamp = 0, source = "X",
            battery = BatteryColumns(level, 300, 4000, "DISCHARGING", plug, "GOOD"),
            memory = null, storage = null,
            network = NetworkColumns(true, net, true, false),
        )
        assertTrue(SampleMapper.sameState(e(50, "NONE", "WIFI"), e(50, "NONE", "WIFI")))
        assertFalse(SampleMapper.sameState(e(50, "NONE", "WIFI"), e(49, "NONE", "WIFI")))
        assertFalse(SampleMapper.sameState(e(50, "NONE", "WIFI"), e(50, "AC", "WIFI")))
        assertFalse(SampleMapper.sameState(e(50, "NONE", "WIFI"), e(50, "NONE", "CELLULAR")))
    }
}

class FormatTest {
    @Test
    fun `formatting`() {
        assertEquals("256.0 GB", Format.bytes(256_000_000_000))
        assertEquals("512 MB", Format.bytes(512_000_000))
        assertEquals("31.2°C", Format.temperature(31.2))
        assertEquals(Format.UNAVAILABLE, Format.temperature(null))
        assertEquals("1 小時 30 分", Format.duration(90 * 60_000L))
        assertEquals("2 天 3 小時", Format.duration((2 * 24 + 3) * 3_600_000L))
        assertEquals("0 分", Format.duration(0))
    }
}
