package com.galaxy.systemmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.systemmonitor.domain.ChartPoint
import com.galaxy.systemmonitor.domain.Series
import com.galaxy.systemmonitor.util.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

data class Threshold(val value: Float, val color: Color)

/**
 * Line chart for recorded samples. Gaps longer than [gapMillis] break the line instead of
 * being bridged, and nothing is interpolated. Tap to inspect the nearest real sample.
 */
@Composable
fun TimeSeriesChart(
    points: List<ChartPoint>,
    start: Long,
    end: Long,
    gapMillis: Long,
    lineColor: Color,
    unit: String,
    modifier: Modifier = Modifier,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    thresholds: List<Threshold> = emptyList(),
    height: Dp = 180.dp,
    valueFormat: (Float) -> String = { String.format(java.util.Locale.US, "%.0f", it) },
) {
    val visible = remember(points, start, end) { points.filter { it.timestamp in start..end }.sortedBy { it.timestamp } }
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    var selected by remember(visible) { mutableStateOf<ChartPoint?>(null) }

    Column(modifier.fillMaxWidth()) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                Text("此區間沒有紀錄", color = labelColor, style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }

        val dataMin = visible.minOf { it.value }
        val dataMax = visible.maxOf { it.value }
        val span = max(dataMax - dataMin, 2f)
        val yMin = fixedMin ?: (dataMin - span * 0.15f)
        val yMax = fixedMax ?: (dataMax + span * 0.15f)
        val segments = remember(visible, gapMillis) { Series.segments(visible, gapMillis) }
        val timePattern = when {
            end - start > 26 * 3_600_000L -> "M/d"
            end - start >= 20 * 3_600_000L -> "M/d HH:mm"
            else -> "HH:mm"
        }
        val fmt = remember(timePattern) { DateTimeFormatter.ofPattern(timePattern).withZone(ZoneId.systemDefault()) }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(visible, start, end) {
                    detectTapGestures { offset ->
                        val left = 40.dp.toPx()
                        val width = size.width - left
                        val t = start + ((offset.x - left) / width * (end - start)).toLong()
                        selected = visible.minByOrNull { abs(it.timestamp - t) }
                    }
                },
        ) {
            val left = 40.dp.toPx()
            val bottom = 18.dp.toPx()
            val top = 8.dp.toPx()
            val w = size.width - left
            val h = size.height - bottom - top
            fun x(t: Long) = left + (t - start).toFloat() / (end - start).coerceAtLeast(1) * w
            fun y(v: Float) = top + h * (1 - ((v - yMin) / (yMax - yMin)).coerceIn(0f, 1f))

            // Grid + y labels
            for (i in 0..2) {
                val v = yMin + (yMax - yMin) * i / 2f
                val yy = y(v)
                drawLine(
                    gridColor.copy(alpha = 0.6f), Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
                val label = textMeasurer.measure(valueFormat(v), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2f))
            }
            // X labels: start, middle, end of the selected range
            listOf(start, (start + end) / 2, end).forEachIndexed { i, t ->
                val label = textMeasurer.measure(fmt.format(Instant.ofEpochMilli(t)), labelStyle)
                val lx = when (i) {
                    0 -> left
                    1 -> x(t) - label.size.width / 2f
                    else -> size.width - label.size.width
                }
                drawText(label, topLeft = Offset(lx, size.height - label.size.height))
            }
            // Threshold lines
            thresholds.filter { it.value in yMin..yMax }.forEach { th ->
                drawLine(
                    th.color.copy(alpha = 0.7f),
                    Offset(left, y(th.value)),
                    Offset(size.width, y(th.value)),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            // Data
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            segments.forEach { seg ->
                if (seg.size == 1) {
                    drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(x(seg[0].timestamp), y(seg[0].value)))
                } else {
                    val path = Path()
                    seg.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(x(p.timestamp), y(p.value)) else path.lineTo(x(p.timestamp), y(p.value))
                    }
                    // Soft area fill under each continuous segment (gaps stay empty).
                    val area = Path().apply {
                        addPath(path)
                        lineTo(x(seg.last().timestamp), top + h)
                        lineTo(x(seg.first().timestamp), top + h)
                        close()
                    }
                    drawPath(
                        area,
                        Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)), startY = top, endY = top + h),
                    )
                    drawPath(path, lineColor, style = stroke)
                }
            }
            // Selection marker
            selected?.let { p ->
                val px = x(p.timestamp)
                drawLine(labelColor.copy(alpha = 0.5f), Offset(px, top), Offset(px, top + h), strokeWidth = 1f)
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(px, y(p.value)))
            }
        }
        val sel = selected
        Text(
            text = if (sel != null) {
                "${Format.fullDateTime(sel.timestamp)}　${valueFormat(sel.value)}$unit"
            } else {
                "點擊圖表可查看單筆紀錄"
            },
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

/** States how much real data the chart actually covers for the selected range. */
@Composable
fun ChartCoverage(points: List<ChartPoint>, start: Long, end: Long) {
    val visible = points.filter { it.timestamp in start..end }
    val text = if (visible.isEmpty()) {
        "所選區間內沒有紀錄"
    } else {
        val first = visible.minOf { it.timestamp }
        val last = visible.maxOf { it.timestamp }
        val partial = first - start > (end - start) / 10
        buildString {
            append("${visible.size} 筆紀錄 · 實際範圍 ${Format.fullDateTime(first)} – ${Format.fullDateTime(last)}")
            if (partial) append("（可用紀錄少於所選區間）")
        }
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
