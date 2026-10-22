package com.galaxy.systemmonitor.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.domain.RateResult
import com.galaxy.systemmonitor.domain.Series
import com.galaxy.systemmonitor.domain.TemperatureAnalyzer
import com.galaxy.systemmonitor.domain.TimeRange
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.BigValue
import com.galaxy.systemmonitor.ui.components.ChartCoverage
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.RangeSelector
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.TimeSeriesChart
import com.galaxy.systemmonitor.ui.components.UnavailableText
import com.galaxy.systemmonitor.ui.components.UsageBar
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format
import java.util.Locale

@Composable
fun BatteryScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drain by vm.drain.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.H24) }
    val status = LocalStatusColors.current

    val samples = history.orEmpty()
    val levelPoints = remember(samples) { Series.levelChart(samples) }
    val tempPoints = remember(samples) { Series.temperatureChart(samples) }
    val gap = Series.gapThreshold(settings.sampleIntervalMinutes)
    val start = now - range.millis

    ScreenScaffold(title = "電池", onBack = onBack) {
        item {
            val reading = live?.battery
            val battery = reading?.valueOrNull()
            SectionCard(title = "目前狀態") {
                if (battery == null) {
                    UnavailableText(reading?.unavailableReason() ?: "讀取中…")
                } else {
                    BigValue(battery.levelPercent?.let { "$it%" } ?: Format.UNAVAILABLE)
                    battery.levelPercent?.let { UsageBar(it / 100f) }
                    MetricRow("充電狀態", battery.status.label)
                    MetricRow("電源", battery.plugType.label)
                    val level = TemperatureAnalyzer.classify(battery.temperatureC, settings.tempWarnC, settings.tempHighC)
                    MetricRow("電池溫度", Format.temperature(battery.temperatureC), status.forTemp(level))
                    MetricRow("電壓", Format.voltage(battery.voltageMv))
                    MetricRow("Android 健康狀態", battery.health.label)
                    MetricRow("電池技術", battery.technology ?: Format.UNAVAILABLE)
                    MetricRow("充電循環次數", battery.cycleCount?.toString() ?: "裝置未提供")
                    live?.let { MetricRow("更新時間", Format.fullDateTime(it.timestamp)) }
                    battery.notes.forEach {
                        Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard(title = "平均耗電速度（放電期間）") {
                val d = drain
                if (d == null) {
                    UnavailableText("計算中…")
                } else {
                    RateRows("最近 1 小時", d.discharge1h, charging = false)
                    RateRows("最近 6 小時", d.discharge6h, charging = false)
                    RateRows("最近 24 小時", d.discharge24h, charging = false)
                }
            }
        }
        item {
            SectionCard(title = "平均充電速度（充電期間）") {
                val d = drain
                if (d == null) {
                    UnavailableText("計算中…")
                } else {
                    RateRows("最近 1 小時", d.charge1h, charging = true)
                    RateRows("最近 6 小時", d.charge6h, charging = true)
                }
            }
        }
        item {
            InfoNote(
                "速度＝（起始電量－結束電量）÷ 經過小時數，只使用相鄰且都在放電（或都在充電）的實際紀錄；" +
                    "間隔超過 3 小時、電量在未接電源時上升，或短時間內異常跳動的區段會被排除。" +
                    "此數值是電量百分比的變化速度，不是電功率，也不是電池容量的精確消耗量；" +
                    "單一時段的快速耗電通常與使用情況有關，不代表電池故障。",
            )
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "電量（%）") {
                TimeSeriesChart(
                    points = levelPoints, start = start, end = now, gapMillis = gap,
                    lineColor = MaterialTheme.colorScheme.primary, unit = "%",
                    fixedMin = 0f, fixedMax = 100f,
                )
                ChartCoverage(levelPoints, start, now)
            }
        }
        item {
            SectionCard(title = "電池溫度（°C）") {
                TimeSeriesChart(
                    points = tempPoints, start = start, end = now, gapMillis = gap,
                    lineColor = MaterialTheme.colorScheme.tertiary, unit = "°C",
                    valueFormat = { String.format(Locale.US, "%.1f", it) },
                )
                ChartCoverage(tempPoints, start, now)
            }
        }
        item {
            InfoNote(
                "電池健康百分比（最大容量）：Android 提供的 BATTERY_PROPERTY_STATE_OF_HEALTH 需要系統層級權限，" +
                    "一般 App 無法讀取，因此本 App 不顯示也不推測此數值。上方的「Android 健康狀態」只是系統回報的概略分類，" +
                    "不等於電池最大容量。部分 One UI 版本可在系統設定或 Samsung Members 中查看官方電池資訊。",
            )
        }
    }
}

@Composable
private fun RateRows(label: String, r: RateResult, charging: Boolean) {
    MetricRow(label, Format.rateLong(r, charging))
    val detail = if (r.firstTimestamp != null && r.lastTimestamp != null) {
        "依據 ${Format.duration(r.coveredMillis)} 的紀錄（${Format.fullDateTime(r.firstTimestamp)} – ${Format.fullDateTime(r.lastTimestamp)}），" +
            "共${if (charging) "上升" else "下降"} ${r.totalPoints} 個百分點"
    } else {
        "此區間沒有可用的${if (charging) "充電" else "放電"}紀錄"
    }
    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
