package com.galaxy.systemmonitor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.domain.Series
import com.galaxy.systemmonitor.util.Format

/** Detailed widget, designed for a 4x4 cell area. */
class LargeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 230.dp),
            DpSize(250.dp, 330.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetStateLoader.load(context)
        provideContent { LargeWidgetContent(rememberWidgetState(context, initial)) }
    }
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LargeWidget()
}

@Composable
private fun LargeWidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val colors = widgetColors(state.settings.widgetStyle)
    val size = LocalSize.current
    val showTrend = state.settings.widgetShowTrend && size.height >= 320.dp

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(colors.background)
            .cornerRadius(24.dp)
            .padding(14.dp),
    ) {
        WidgetHeader(state, colors, "Galaxy System Monitor")
        Spacer(GlanceModifier.height(8.dp))

        if (state.fatalError != null) {
            Text(state.fatalError, style = TextStyle(color = colors.warn, fontSize = 13.sp))
            return@Column
        }

        val battery = state.battery
        // Battery headline
        Column(
            modifier = GlanceModifier.fillMaxWidth()
                .clickable(actionStartActivity(Destinations.intent(context, Destinations.BATTERY))),
        ) {
            Row(verticalAlignment = Alignment.Vertical.Bottom, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    battery?.levelPercent?.let { "$it%" } ?: Format.UNAVAILABLE,
                    style = TextStyle(color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().padding(bottom = 4.dp)) {
                    Text(
                        battery?.let {
                            if (it.pluggedIn == true) "${it.status.label} · ${it.plugType.label}" else it.status.label
                        } ?: (state.batteryError ?: "電池資訊不可用"),
                        maxLines = 1,
                        style = TextStyle(color = colors.subtext, fontSize = 12.sp),
                    )
                    Text(
                        "電池溫度 ${Format.temperature(battery?.temperatureC)} · ${state.tempLevel.label}",
                        maxLines = 1,
                        style = TextStyle(color = colors.forTemp(state.tempLevel), fontSize = 12.sp),
                    )
                }
            }
            if (battery?.levelPercent != null) {
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = battery.levelPercent / 100f,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = colors.accent,
                    backgroundColor = colors.track,
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))

        val rate = rateTile(state)
        val memory = state.memory
        val storage = state.storage
        // Glance allows at most 10 children per container, so sections are grouped.
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetTile(
                label = rate.label, value = rate.value, sub = rate.sub ?: "依實際電量紀錄計算",
                route = Destinations.BATTERY, colors = colors,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetTile(
                label = "系統記憶體",
                value = memory?.let { Format.percent(it.usedFraction) } ?: Format.UNAVAILABLE,
                sub = memory?.let { "${Format.bytes(it.usedBytes)} / ${Format.bytes(it.totalBytes)}" },
                route = Destinations.MEMORY, colors = colors,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetTile(
                label = "剩餘儲存空間",
                value = storage?.let { Format.bytes(it.freeBytes) } ?: Format.UNAVAILABLE,
                sub = storage?.let { "已用 ${Format.percent(it.usedFraction)}" + if (state.lowStorage) " · 偏低" else "" },
                route = Destinations.STORAGE, colors = colors,
                valueColor = if (state.lowStorage) colors.warn else colors.text,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetTile(
                label = "網路", value = networkValue(state.network), sub = networkSub(state.network),
                route = Destinations.NETWORK, colors = colors,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
        }

        if (showTrend) Column(GlanceModifier.fillMaxWidth()) {
            Spacer(GlanceModifier.height(8.dp))
            Text("近 24 小時電量", style = TextStyle(color = colors.subtext, fontSize = 11.sp))
            Spacer(GlanceModifier.height(2.dp))
            if (state.trend.size >= 2) {
                val density = context.resources.displayMetrics.density
                val widthPx = ((size.width.value - 28) * density).toInt().coerceIn(100, 900)
                val heightPx = (48 * density).toInt()
                val bitmap = TrendBitmap.render(
                    points = state.trend,
                    start = state.trendStart,
                    end = state.trendEnd,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    lineColor = colors.trendLine,
                    gridColor = colors.trendGrid,
                    maxGapMillis = Series.gapThreshold(state.settings.sampleIntervalMinutes),
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "近 24 小時電量趨勢",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(48.dp)
                        .clickable(actionStartActivity(Destinations.intent(context, Destinations.BATTERY))),
                )
            } else {
                Box(GlanceModifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Text("紀錄不足，累積後顯示", style = TextStyle(color = colors.subtext, fontSize = 11.sp))
                }
            }
        }

        Spacer(GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier.fillMaxWidth()
                .background(colors.card)
                .cornerRadius(12.dp)
                .padding(vertical = 7.dp)
                .clickable(actionStartActivity(Destinations.intent(context, Destinations.DASHBOARD))),
            contentAlignment = Alignment.Center,
        ) {
            Text("開啟詳細資訊 ›", style = TextStyle(color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}
