package com.galaxy.systemmonitor.domain

import com.galaxy.systemmonitor.data.db.SampleEntity

data class DrainSummary(
    val discharge1h: RateResult,
    val discharge6h: RateResult,
    val discharge24h: RateResult,
    val charge1h: RateResult,
    val charge6h: RateResult,
) {
    /** Best short-term discharge figure for compact displays (1 h, falling back to 6 h). */
    val headline: RateResult
        get() = if (discharge1h.status == RateStatus.OK) discharge1h else discharge6h

    companion object {
        fun from(samples: List<SampleEntity>, now: Long): DrainSummary {
            val points = Series.batteryPoints(samples)
            val h = DrainCalculator.HOUR
            return DrainSummary(
                discharge1h = DrainCalculator.discharge(points, now, h),
                discharge6h = DrainCalculator.discharge(points, now, 6 * h),
                discharge24h = DrainCalculator.discharge(points, now, 24 * h),
                charge1h = DrainCalculator.charge(points, now, h),
                charge6h = DrainCalculator.charge(points, now, 6 * h),
            )
        }
    }
}
