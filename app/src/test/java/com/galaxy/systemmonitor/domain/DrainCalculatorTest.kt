package com.galaxy.systemmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrainCalculatorTest {
    private val h = DrainCalculator.HOUR
    private val m = DrainCalculator.MINUTE
    private val now = 1_000 * h

    private fun p(minutesAgo: Long, level: Int, plugged: Boolean? = false) =
        BatteryPoint(now - minutesAgo * m, level, plugged)

    @Test
    fun `example from spec - 80 to 76 over 2 hours is 2 points per hour`() {
        val points = listOf(p(120, 80), p(60, 78), p(0, 76))
        val r = DrainCalculator.discharge(points, now, 6 * h)
        assertEquals(RateStatus.OK, r.status)
        assertEquals(2.0, r.pointsPerHour!!, 1e-9)
        assertEquals(4, r.totalPoints)
        assertEquals(2 * h, r.coveredMillis)
        assertEquals(now - 2 * h, r.firstTimestamp)
        assertEquals(now, r.lastTimestamp)
    }

    @Test
    fun `charging intervals are excluded from discharge and never negative`() {
        val points = listOf(
            p(180, 50), p(120, 46), // discharge 4 in 1h
            p(110, 46, true), p(60, 70, true), // charging
            p(50, 70), p(0, 68), // discharge 2 in 50 min
        )
        val r = DrainCalculator.discharge(points, now, 6 * h)
        assertEquals(RateStatus.OK, r.status)
        assertEquals(6, r.totalPoints)
        assertEquals(110 * m, r.coveredMillis)
        assertEquals(6.0 / (110.0 / 60.0), r.pointsPerHour!!, 1e-9)
        val c = DrainCalculator.charge(points, now, 6 * h)
        assertEquals(24, c.totalPoints)
    }

    @Test
    fun `too little covered time reports insufficient data`() {
        val points = listOf(p(10, 80), p(0, 79))
        val r = DrainCalculator.discharge(points, now, h)
        assertEquals(RateStatus.INSUFFICIENT_DATA, r.status)
        assertNull(r.pointsPerHour)
    }

    @Test
    fun `no samples reports insufficient data`() {
        val r = DrainCalculator.discharge(emptyList(), now, h)
        assertEquals(RateStatus.INSUFFICIENT_DATA, r.status)
        assertNull(r.pointsPerHour)
    }

    @Test
    fun `only charging in window reports other mode`() {
        val points = listOf(p(60, 40, true), p(30, 50, true), p(0, 60, true))
        val r = DrainCalculator.discharge(points, now, h)
        assertEquals(RateStatus.OTHER_MODE_ONLY, r.status)
        assertNull(r.pointsPerHour)
    }

    @Test
    fun `long gaps are not bridged`() {
        // 5 h gap may hide an unseen charge; it must not be used.
        val points = listOf(p(400, 90), p(100, 60), p(50, 59), p(0, 58))
        val r = DrainCalculator.discharge(points, now, 24 * h)
        assertEquals(2, r.intervalsUsed)
        assertEquals(2, r.totalPoints)
    }

    @Test
    fun `level rising while unplugged is ignored`() {
        val points = listOf(p(90, 50), p(60, 70), p(30, 69), p(0, 68))
        val r = DrainCalculator.discharge(points, now, 6 * h)
        assertEquals(2, r.intervalsUsed)
        assertEquals(2, r.totalPoints)
    }

    @Test
    fun `sudden jump is treated as glitch`() {
        // 30 points in 2 minutes is not a real drain rate.
        val points = listOf(p(62, 80), p(60, 50), p(30, 49), p(0, 48))
        val r = DrainCalculator.discharge(points, now, 6 * h)
        assertEquals(2, r.intervalsUsed)
        assertEquals(2, r.totalPoints)
    }

    @Test
    fun `unknown plug state is not used`() {
        val points = listOf(p(60, 80, null), p(0, 78, null))
        val r = DrainCalculator.discharge(points, now, h)
        assertEquals(RateStatus.INSUFFICIENT_DATA, r.status)
    }

    @Test
    fun `full battery topping off does not count as charge speed`() {
        val points = listOf(p(60, 100, true), p(30, 100, true), p(0, 100, true))
        val r = DrainCalculator.charge(points, now, h)
        assertEquals(0, r.intervalsUsed)
    }

    @Test
    fun `points outside the window are ignored`() {
        val points = listOf(p(300, 90), p(240, 80), p(60, 50), p(0, 48))
        val r = DrainCalculator.discharge(points, now, h)
        assertEquals(1, r.intervalsUsed)
        assertEquals(2.0, r.pointsPerHour!!, 1e-9)
    }

    @Test
    fun `minimum coverage grows with window`() {
        assertEquals(30 * m, DrainCalculator.minCoverage(h))
        assertEquals(90 * m, DrainCalculator.minCoverage(6 * h))
        assertEquals(4 * h, DrainCalculator.minCoverage(24 * h))
    }
}
