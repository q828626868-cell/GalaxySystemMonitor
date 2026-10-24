package com.galaxy.systemmonitor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.domain.TempLevel
import com.galaxy.systemmonitor.util.Format

/** Compact widget, designed for a 4x2 cell area: temperature, memory, storage and network at a glance. */
class SmallWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 100.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 150.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetStateLoader.load(context)
        provideContent { SmallWidgetContent(rememberWidgetState(context, initial)) }
    }
}

class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallWidget()
}

/** One metric: colored status dot + label, a big value, then a usage bar or a short status line. */
private data class Metric(
    val label: String,
    val value: String,
    val color: ColorProvider,
    val route: String,
    val progress: Float?,
    val sub: String?,
)

@Composable
private fun SmallWidgetContent(state: WidgetState) {
    val colors = widgetColors(state.settings.widgetStyle)
    val size = LocalSize.current
    val narrow = size.width < 240.dp
    // Too short for a 2 × 2 grid: put the four tiles in one row and drop the bars.
    val singleRow = size.height < 140.dp && !narrow

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(colors.background)
            .cornerRadius(22.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WidgetHeader(state, colors, if (narrow) "系統監控" else "Galaxy Monitor")
        Spacer(GlanceModifier.height(6.dp))

        if (state.fatalError != null) {
            Text(state.fatalError, style = TextStyle(color = colors.warn, fontSize = 13.sp))
            return@Column
        }

        val metrics = metrics(state, colors)
        val valueSize = when {
            singleRow -> 15.sp
            narrow -> 16.sp
            else -> 20.sp
        }
        if (singleRow) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                metrics.forEachIndexed { i, m ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    MetricTile(m, colors, valueSize, showBar = false, GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        } else {
            metrics.chunked(2).forEachIndexed { row, pair ->
                if (row > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    pair.forEachIndexed { i, m ->
                        if (i > 0) Spacer(GlanceModifier.width(6.dp))
                        MetricTile(m, colors, valueSize, showBar = true, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
    }
}

private fun metrics(state: WidgetState, colors: WidgetColors): List<Metric> {
    val t = state.battery?.temperatureC
    val memory = state.memory
    val storage = state.storage
    val net = state.network
    val netOk = net?.connected == true && net.validated == true
    return listOf(
        Metric(
            label = "電池溫度",
            value = Format.temperature(t),
            color = when (state.tempLevel) {
                TempLevel.NORMAL -> colors.accent
                TempLevel.ELEVATED -> colors.warn
                TempLevel.HIGH -> colors.danger
                TempLevel.UNAVAILABLE -> colors.subtext
            },
            route = Destinations.TEMPERATURE,
            // A status word reads faster than a bar for temperature.
            progress = null,
            sub = state.tempLevel.label,
        ),
        Metric(
            label = "記憶體",
            value = memory?.let { Format.percent(it.usedFraction) } ?: Format.UNAVAILABLE,
            color = colors.accent,
            route = Destinations.MEMORY,
            progress = memory?.usedFraction?.toFloat(),
            sub = memory?.let { "可用 ${Format.bytes(it.availableBytes)}" },
        ),
        Metric(
            label = "儲存空間",
            value = storage?.let { Format.bytes(it.freeBytes) } ?: Format.UNAVAILABLE,
            color = if (state.lowStorage) colors.warn else colors.accent,
            route = Destinations.STORAGE,
            progress = storage?.usedFraction?.toFloat(),
            sub = storage?.let { if (state.lowStorage) "剩餘偏低" else "剩餘 ${Format.percent(it.freeFraction)}" },
        ),
        Metric(
            label = "網路",
            value = networkValue(net),
            color = when {
                net == null -> colors.subtext
                !net.connected -> colors.danger
                netOk -> colors.accent
                else -> colors.warn
            },
            route = Destinations.NETWORK,
            progress = null,
            sub = networkSub(net) ?: "沒有預設網路",
        ),
    )
}

@Composable
private fun MetricTile(m: Metric, colors: WidgetColors, valueSize: TextUnit, showBar: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(colors.card)
            .cornerRadius(16.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(actionStartActivity(Destinations.intent(context, m.route))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(GlanceModifier.size(7.dp).cornerRadius(4.dp).background(m.color)) {}
            Spacer(GlanceModifier.width(5.dp))
            Text(m.label, maxLines = 1, style = TextStyle(color = colors.subtext, fontSize = 11.sp))
        }
        Text(
            m.value,
            maxLines = 1,
            style = TextStyle(
                color = if (m.color == colors.accent) colors.text else m.color,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (showBar) {
            if (m.progress != null) {
                Spacer(GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = m.progress.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = m.color,
                    backgroundColor = colors.track,
                )
            } else if (m.sub != null) {
                Text(m.sub, maxLines = 1, style = TextStyle(color = m.color, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}
