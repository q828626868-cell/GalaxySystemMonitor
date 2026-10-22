package com.galaxy.systemmonitor.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.galaxy.systemmonitor.domain.ChartPoint
import com.galaxy.systemmonitor.domain.Series

/**
 * Small battery-level sparkline for the large widget. Gaps in the history are left as
 * gaps; no values are interpolated.
 */
object TrendBitmap {

    fun render(
        points: List<ChartPoint>,
        start: Long,
        end: Long,
        widthPx: Int,
        heightPx: Int,
        lineColor: Int,
        gridColor: Int,
        maxGapMillis: Long,
    ): Bitmap {
        val bmp = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        val canvas = Canvas(bmp)
        val pad = heightPx * 0.08f
        val h = heightPx - 2 * pad
        val span = (end - start).coerceAtLeast(1).toFloat()

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridColor
            strokeWidth = 1f
        }
        for (pct in listOf(0, 50, 100)) {
            val y = pad + h * (1 - pct / 100f)
            canvas.drawLine(0f, y, widthPx.toFloat(), y, grid)
        }

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            strokeWidth = heightPx / 40f + 2f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.FILL
        }
        fun x(t: Long) = (t - start) / span * widthPx
        fun y(v: Float) = pad + h * (1 - v.coerceIn(0f, 100f) / 100f)

        for (segment in Series.segments(points.filter { it.timestamp in start..end }, maxGapMillis)) {
            if (segment.size == 1) {
                canvas.drawCircle(x(segment[0].timestamp), y(segment[0].value), line.strokeWidth, dot)
                continue
            }
            val path = Path()
            segment.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p.timestamp), y(p.value)) else path.lineTo(x(p.timestamp), y(p.value))
            }
            canvas.drawPath(path, line)
        }
        return bmp
    }
}
