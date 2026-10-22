package com.galaxy.systemmonitor.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.R
import com.galaxy.systemmonitor.data.model.NetworkInfo
import com.galaxy.systemmonitor.data.settings.WidgetStyle
import com.galaxy.systemmonitor.domain.RateResult
import com.galaxy.systemmonitor.domain.TempLevel
import com.galaxy.systemmonitor.util.Format
import androidx.glance.color.ColorProvider as DayNightColorProvider

data class WidgetColors(
    val background: ColorProvider,
    val card: ColorProvider,
    val text: ColorProvider,
    val subtext: ColorProvider,
    val accent: ColorProvider,
    val warn: ColorProvider,
    val danger: ColorProvider,
    val track: ColorProvider,
    /** Plain ARGB for the bitmap sparkline (must read on both light and dark backgrounds). */
    val trendLine: Int,
    val trendGrid: Int,
)

private val Accent = Color(0xFF6CC4B0)
private val Warn = Color(0xFFD8B25A)
private val Danger = Color(0xFFE57A6E)

fun widgetColors(style: WidgetStyle): WidgetColors = when (style) {
    WidgetStyle.AMOLED -> WidgetColors(
        background = ColorProvider(Color(0xFF000000)),
        card = ColorProvider(Color(0xFF101618)),
        text = ColorProvider(Color(0xFFE6ECEB)),
        subtext = ColorProvider(Color(0xFF8A9A98)),
        accent = ColorProvider(Accent),
        warn = ColorProvider(Warn),
        danger = ColorProvider(Danger),
        track = ColorProvider(Color(0xFF1F2A2C)),
        trendLine = Accent.toArgb(),
        trendGrid = Color(0xFF1F2A2C).toArgb(),
    )
    WidgetStyle.GRAPHITE -> WidgetColors(
        background = ColorProvider(Color(0xFF15191B)),
        card = ColorProvider(Color(0xFF20272A)),
        text = ColorProvider(Color(0xFFE6ECEB)),
        subtext = ColorProvider(Color(0xFF93A3A1)),
        accent = ColorProvider(Accent),
        warn = ColorProvider(Warn),
        danger = ColorProvider(Danger),
        track = ColorProvider(Color(0xFF2E383B)),
        trendLine = Accent.toArgb(),
        trendGrid = Color(0xFF2E383B).toArgb(),
    )
    WidgetStyle.FOLLOW_SYSTEM -> WidgetColors(
        background = DayNightColorProvider(day = Color(0xFFF2F5F5), night = Color(0xFF000000)),
        card = DayNightColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF101618)),
        text = DayNightColorProvider(day = Color(0xFF15201E), night = Color(0xFFE6ECEB)),
        subtext = DayNightColorProvider(day = Color(0xFF5C6B69), night = Color(0xFF8A9A98)),
        accent = DayNightColorProvider(day = Color(0xFF2E7D6E), night = Accent),
        warn = DayNightColorProvider(day = Color(0xFF8A5F00), night = Warn),
        danger = DayNightColorProvider(day = Color(0xFFB3261E), night = Danger),
        track = DayNightColorProvider(day = Color(0xFFDCE4E3), night = Color(0xFF1F2A2C)),
        trendLine = Color(0xFF4FA894).toArgb(),
        trendGrid = Color(0x553F4F4D).toArgb(),
    )
}

fun WidgetColors.forTemp(level: TempLevel): ColorProvider = when (level) {
    TempLevel.NORMAL -> text
    TempLevel.ELEVATED -> warn
    TempLevel.HIGH -> danger
    TempLevel.UNAVAILABLE -> subtext
}

@Composable
fun WidgetHeader(state: WidgetState, colors: WidgetColors, title: String) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight()
                .clickable(actionStartActivity(Destinations.intent(context, Destinations.DASHBOARD))),
        )
        Text(
            text = state.updatedAt?.let { "更新 ${Format.dateTime(it)}" } ?: "尚未更新",
            maxLines = 1,
            style = TextStyle(color = colors.subtext, fontSize = 11.sp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "重新整理",
            modifier = GlanceModifier.size(24.dp).padding(2.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@Composable
fun WidgetTile(
    label: String,
    value: String,
    sub: String?,
    route: String,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
    valueColor: ColorProvider = colors.text,
    valueSize: TextUnit = 18.sp,
    padding: Dp = 8.dp,
) {
    val context = LocalContext.current
    // Status words such as "資料不足" are wider than numbers; keep them readable instead of truncated.
    val isStatusText = value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } && value.length >= 4
    Column(
        modifier = modifier
            .background(colors.card)
            .cornerRadius(14.dp)
            .padding(padding)
            .clickable(actionStartActivity(Destinations.intent(context, route))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = colors.subtext, fontSize = 11.sp))
        Text(
            value,
            maxLines = 1,
            style = TextStyle(
                color = valueColor,
                fontSize = if (isStatusText) 13.sp else valueSize,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (sub != null) {
            Text(sub, maxLines = 1, style = TextStyle(color = colors.subtext, fontSize = 10.sp))
        }
    }
}

fun networkValue(info: NetworkInfo?): String = when {
    info == null -> Format.UNAVAILABLE
    !info.connected -> "無連線"
    else -> info.transport.label
}

fun networkSub(info: NetworkInfo?): String? = when {
    info == null || !info.connected -> null
    info.validated == true -> "可上網"
    info.validated == false -> "未驗證"
    else -> "未知"
}

/** Label / value / sub-label for the "rate" tile: charge speed while plugged, otherwise drain. */
data class RateTile(val label: String, val value: String, val sub: String?)

fun rateTile(state: WidgetState): RateTile {
    val drain = state.drain ?: return RateTile("耗電速度", Format.UNAVAILABLE, state.historyError)
    return if (state.battery?.pluggedIn == true) {
        val r = drain.charge1h.takeIf { it.pointsPerHour != null } ?: drain.charge6h
        RateTile("充電速度", Format.rateShort(r, charging = true), windowLabel(r))
    } else {
        val r = drain.headline
        RateTile("耗電速度", Format.rateShort(r), windowLabel(r))
    }
}

private fun windowLabel(r: RateResult): String? =
    if (r.pointsPerHour != null) "近 ${r.windowMillis / 3_600_000} 小時平均" else null
