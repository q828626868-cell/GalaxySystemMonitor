package com.galaxy.systemmonitor.data.source

import com.galaxy.systemmonitor.data.model.BatteryHealth
import com.galaxy.systemmonitor.data.model.ChargeStatus
import com.galaxy.systemmonitor.data.model.PlugType
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryParserTest {
    private fun raw(
        level: Int = 76, scale: Int = 100, temp: Int? = 312, voltage: Int? = 4012,
        status: Int = 3, plugged: Int = 0, health: Int = 2, cycle: Int = -1,
    ) = BatteryParser.Raw(level, scale, temp, voltage, status, plugged, health, "Li-ion", cycle)

    @Test
    fun `normal values are parsed`() {
        val b = BatteryParser.parse(raw())
        assertEquals(76, b.levelPercent)
        assertEquals(31.2, b.temperatureC!!, 1e-9)
        assertEquals(4012, b.voltageMv)
        assertEquals(ChargeStatus.DISCHARGING, b.status)
        assertEquals(PlugType.NONE, b.plugType)
        assertEquals(false, b.pluggedIn)
        assertEquals(BatteryHealth.GOOD, b.health)
        assertNull(b.cycleCount)
        assertTrue(b.notes.isEmpty())
    }

    @Test
    fun `non-100 scale is normalised`() {
        assertEquals(50, BatteryParser.parse(raw(level = 128, scale = 256)).levelPercent)
    }

    @Test
    fun `missing level is null not zero`() {
        val b = BatteryParser.parse(raw(level = -1, scale = -1))
        assertNull(b.levelPercent)
        assertTrue(b.notes.isNotEmpty())
    }

    @Test
    fun `missing or zero or absurd temperature is unavailable`() {
        assertNull(BatteryParser.parse(raw(temp = null)).temperatureC)
        assertNull(BatteryParser.parse(raw(temp = Int.MIN_VALUE)).temperatureC)
        assertNull(BatteryParser.parse(raw(temp = 0)).temperatureC)
        assertNull(BatteryParser.parse(raw(temp = 2000)).temperatureC)
        assertNull(BatteryParser.parse(raw(temp = -500)).temperatureC)
    }

    @Test
    fun `voltage reported in volts is converted and zero is unavailable`() {
        assertEquals(4000, BatteryParser.parse(raw(voltage = 4)).voltageMv)
        assertNull(BatteryParser.parse(raw(voltage = 0)).voltageMv)
        assertNull(BatteryParser.parse(raw(voltage = null)).voltageMv)
        assertNull(BatteryParser.parse(raw(voltage = 99_999)).voltageMv)
    }

    @Test
    fun `plug types and unknown plug state`() {
        assertEquals(PlugType.AC, BatteryParser.parse(raw(plugged = 1)).plugType)
        assertEquals(PlugType.USB, BatteryParser.parse(raw(plugged = 2)).plugType)
        assertEquals(PlugType.WIRELESS, BatteryParser.parse(raw(plugged = 4)).plugType)
        val unknown = BatteryParser.parse(raw(plugged = -1))
        assertEquals(PlugType.UNKNOWN, unknown.plugType)
        assertNull(unknown.pluggedIn)
    }

    @Test
    fun `charging and cycle count`() {
        val b = BatteryParser.parse(raw(status = 2, plugged = 1, cycle = 213))
        assertEquals(ChargeStatus.CHARGING, b.status)
        assertEquals(true, b.pluggedIn)
        assertEquals(213, b.cycleCount)
    }

    @Test
    fun `zero cycle count is treated as not provided`() {
        val b = BatteryParser.parse(raw(cycle = 0))
        assertNull(b.cycleCount)
        assertTrue(b.notes.isNotEmpty())
    }

    @Test
    fun `state of health requires permission`() {
        val b = BatteryParser.parse(raw().copy(stateOfHealth = 92, stateOfHealthPermitted = false))
        assertEquals(BatteryParser.SOH_PERMISSION_REASON, b.stateOfHealth.unavailableReason())
    }

    @Test
    fun `state of health is parsed when permitted`() {
        val b = BatteryParser.parse(raw().copy(stateOfHealth = 92, stateOfHealthPermitted = true))
        assertEquals(92, b.stateOfHealth.valueOrNull())
    }

    @Test
    fun `unsupported or abnormal state of health is unavailable`() {
        val unsupported = BatteryParser.parse(raw().copy(stateOfHealthPermitted = true))
        assertNull(unsupported.stateOfHealth.valueOrNull())
        val abnormal = BatteryParser.parse(raw().copy(stateOfHealth = 0, stateOfHealthPermitted = true))
        assertNull(abnormal.stateOfHealth.valueOrNull())
        assertTrue(abnormal.notes.isNotEmpty())
    }
}
