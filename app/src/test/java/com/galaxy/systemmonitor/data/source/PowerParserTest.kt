package com.galaxy.systemmonitor.data.source

import com.galaxy.systemmonitor.domain.PowerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerParserTest {
    private fun raw(current: Int = 542_968, avg: Int = Int.MIN_VALUE, counter: Int = 3_572_100, voltage: Int? = 4_000) =
        PowerParser.Raw(timestamp = 1_000, currentNow = current, currentAverage = avg, chargeCounter = counter, voltageMv = voltage)

    @Test
    fun `microamps are converted and power is computed`() {
        val p = PowerParser.parse(raw())
        assertEquals(542, p.currentMa)
        assertEquals(2.168, p.powerW!!, 1e-9)
        assertEquals(3572, p.chargeCounterMah)
        assertNull(p.averageCurrentMa)
        assertTrue(p.notes.isEmpty())
    }

    @Test
    fun `vendor milliamps are kept and discharge is negative`() {
        val p = PowerParser.parse(raw(current = -1732))
        assertEquals(-1732, p.currentMa)
        assertTrue(p.powerW!! < 0)
    }

    @Test
    fun `unsupported current is unavailable, not zero`() {
        val p = PowerParser.parse(raw(current = Int.MIN_VALUE))
        assertNull(p.currentMa)
        assertNull(p.powerW)
        assertTrue(p.notes.isNotEmpty())
    }

    @Test
    fun `implausible current is rejected`() {
        val p = PowerParser.parse(raw(current = 30_000_000))
        assertNull(p.currentMa)
        assertTrue(p.notes.isNotEmpty())
    }

    @Test
    fun `missing voltage gives no power`() {
        assertNull(PowerParser.parse(raw(voltage = null)).powerW)
    }

    @Test
    fun `session keeps a rolling window and peaks`() {
        val a = PowerParser.parse(raw()).copy(timestamp = 0)
        val b = PowerParser.parse(raw(current = 6_000_000)).copy(timestamp = 1_000)
        val c = PowerParser.parse(raw(current = -1_000_000)).copy(timestamp = 20_000)
        val s = PowerSession().append(a, 10_000).append(b, 10_000).append(c, 10_000)
        assertEquals(1, s.points.size)
        assertEquals(24.0, s.peakChargeW!!, 1e-9)
        assertEquals(4.0, s.peakDischargeW!!, 1e-9)
    }
}
