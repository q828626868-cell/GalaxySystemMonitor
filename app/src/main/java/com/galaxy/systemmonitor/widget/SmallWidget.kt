package com.galaxy.systemmonitor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.util.Format

/** Compact widget, designed for a 4x2 cell area. */
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

@Composable
private fun SmallWidgetContent(state: WidgetState) {
    val colors = widgetColors(state.settings.widgetStyle)
    val size = LocalSize.current
    val narrow = size.width < 240.dp
    val tall = size.height >= 140.dp

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

        val battery = state.battery
        val rate = rateTile(state)
        val valueSize = if (narrow) 15.sp else 18.sp
        val pad = if (narrow) 6.dp else 8.dp

        @Composable
        fun LevelTile(m: GlanceModifier) = WidgetTile(
            label = "電量",
            value = battery?.levelPercent?.let { "$it%" } ?: Format.UNAVAILABLE,
            sub = battery?.status?.label ?: state.batteryError?.let { "讀取失敗" },
            route = Destinations.BATTERY, colors = colors, modifier = m, valueSize = valueSize, padding = pad,
        )

        @Composable
        fun TempTile(m: GlanceModifier) = WidgetTile(
            label = "電池溫度",
            value = Format.temperature(battery?.temperatureC),
            sub = state.tempLevel.label,
            route = Destinations.TEMPERATURE, colors = colors, modifier = m,
            valueColor = colors.forTemp(state.tempLevel), valueSize = valueSize, padding = pad,
        )

        @Composable
        fun RateTileView(m: GlanceModifier) = WidgetTile(
            label = rate.label, value = rate.value, sub = rate.sub,
            route = Destinations.BATTERY, colors = colors, modifier = m, valueSize = valueSize, padding = pad,
        )

        @Composable
        fun NetTile(m: GlanceModifier) = WidgetTile(
            label = "網路", value = networkValue(state.network), sub = networkSub(state.network),
            route = Destinations.NETWORK, colors = colors, modifier = m, valueSize = valueSize, padding = pad,
        )

        if (narrow) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                LevelTile(GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(5.dp))
                TempTile(GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Spacer(GlanceModifier.height(5.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                RateTileView(GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(5.dp))
                NetTile(GlanceModifier.defaultWeight().fillMaxHeight())
            }
        } else {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                LevelTile(GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(6.dp))
                TempTile(GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(6.dp))
                RateTileView(GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(6.dp))
                NetTile(GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }

        if (tall) {
            Spacer(GlanceModifier.height(6.dp))
            val context = LocalContext.current
            val storage = state.storage
            val storageText = if (storage != null) {
                "剩餘儲存空間 ${Format.bytes(storage.freeBytes)}（${Format.percent(storage.freeFraction)}）"
            } else {
                "剩餘儲存空間：${Format.UNAVAILABLE}"
            }
            Text(
                text = storageText + if (state.lowStorage) " · 空間偏低" else "",
                maxLines = 1,
                style = TextStyle(color = if (state.lowStorage) colors.warn else colors.subtext, fontSize = 11.sp),
                modifier = GlanceModifier.fillMaxWidth()
                    .clickable(actionStartActivity(Destinations.intent(context, Destinations.STORAGE))),
            )
        }
    }
}
