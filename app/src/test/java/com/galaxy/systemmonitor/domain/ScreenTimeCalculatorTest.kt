package com.galaxy.systemmonitor.domain

import com.galaxy.systemmonitor.domain.ScreenTimeCalculator.Event
import com.galaxy.systemmonitor.domain.ScreenTimeCalculator.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeCalculatorTest {
    private fun ev(t: Long, pkg: String, kind: Kind, cls: String = "Main") = Event(t, pkg, cls, kind)

    @Test
    fun `resume to pause is counted`() {
        val r = ScreenTimeCalculator.compute(
            listOf(ev(100, "a", Kind.RESUMED), ev(400, "a", Kind.PAUSED)),
            start = 0, end = 1_000,
        )
        assertEquals(300L, r["a"])
    }

    @Test
    fun `session is clipped to the range`() {
        val r = ScreenTimeCalculator.compute(
            listOf(ev(100, "a", Kind.RESUMED), ev(900, "a", Kind.PAUSED)),
            start = 500, end = 700,
        )
        assertEquals(200L, r["a"])
    }

    @Test
    fun `activity switch inside one app is not a gap`() {
        val r = ScreenTimeCalculator.compute(
            listOf(
                ev(0, "a", Kind.RESUMED, "One"),
                ev(100, "a", Kind.RESUMED, "Two"),
                ev(110, "a", Kind.PAUSED, "One"),
                ev(300, "a", Kind.PAUSED, "Two"),
            ),
            start = 0, end = 1_000,
        )
        assertEquals(300L, r["a"])
    }

    @Test
    fun `screen off closes open sessions`() {
        val r = ScreenTimeCalculator.compute(
            listOf(ev(0, "a", Kind.RESUMED), ev(250, "android", Kind.SCREEN_OFF)),
            start = 0, end = 1_000,
        )
        assertEquals(250L, r["a"])
    }

    @Test
    fun `still open app counts up to end, pause without resume is ignored`() {
        val r = ScreenTimeCalculator.compute(
            listOf(ev(50, "b", Kind.PAUSED), ev(600, "a", Kind.RESUMED)),
            start = 0, end = 1_000,
        )
        assertEquals(400L, r["a"])
        assertNull(r["b"])
    }

    @Test
    fun `raw event types are mapped`() {
        assertEquals(Kind.RESUMED, ScreenTimeCalculator.kindOf(1))
        assertEquals(Kind.PAUSED, ScreenTimeCalculator.kindOf(2))
        assertEquals(Kind.SCREEN_OFF, ScreenTimeCalculator.kindOf(16))
        assertNull(ScreenTimeCalculator.kindOf(5))
    }
}
