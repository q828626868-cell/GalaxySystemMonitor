package com.galaxy.systemmonitor.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.HeroCard
import com.galaxy.systemmonitor.ui.components.HeroLabel
import com.galaxy.systemmonitor.ui.components.HeroPill
import com.galaxy.systemmonitor.ui.components.HeroPills
import com.galaxy.systemmonitor.ui.components.HeroSubtitle
import com.galaxy.systemmonitor.ui.components.HeroValue
import com.galaxy.systemmonitor.ui.components.RingGauge
import com.galaxy.systemmonitor.ui.components.StatColumn
import com.galaxy.systemmonitor.ui.components.StatRow
import com.galaxy.systemmonitor.ui.components.StatTile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.model.valueOrNull
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
import com.galaxy.systemmonitor.ui.components.StatusChip
import com.galaxy.systemmonitor.ui.components.Threshold
import com.galaxy.systemmonitor.ui.components.TimeSeriesChart
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format
import java.util.Locale

@Composable
fun TemperatureScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val maxT by vm.maxTemperature.collectAsStateWithLifecycle()
    val minT by vm.minTemperature.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.H24) }
    val status = LocalStatusColors.current

    val samples = history.orEmpty()
    val points = remember(samples) { Series.tempPoints(samples) }
    val chart = remember(samples) { Series.temperatureChart(samples) }
    val battery = live?.battery?.valueOrNull()
    val level = TemperatureAnalyzer.classify(battery?.temperatureC, settings.tempWarnC, settings.tempHighC)
    val start = now - range.millis

    ScreenScaffold(title = "電池溫度", onBack = onBack) {
        item {
            val t = battery?.temperatureC
            val color = status.forTemp(level)
            HeroCard(
                trailing = {
                    // Ring fills toward the high-warning threshold (+10 °C headroom so it never looks "full" too early).
                    RingGauge(t?.let { (it / (settings.tempHighC + 10)).toFloat() }, color, centerText = level.label)
                },
            ) {
                HeroLabel("目前溫度")
                HeroValue(Format.temperature(t), color)
                live?.let { HeroSubtitle("更新於 ${Format.dateTime(it.timestamp)}") }
                HeroPills {
                    HeroPill("偏高 ${settings.tempWarnC}°C", status.warn)
                    HeroPill("高溫 ${settings.tempHighC}°C", status.danger)
                }
            }
        }
        item {
            SectionCard(title = "近期變化") {
                val windows = listOf(TimeRange.H1, TimeRange.H6, TimeRange.H24)
                    .map { it to TemperatureAnalyzer.windowStats(points, now, it.millis) }
                Row(Modifier.fillMaxWidth()) {
                    windows.forEach { (r, s) ->
                        StatColumn(
                            Modifier.weight(1f),
                            s?.let { String.format(Locale.US, "%+.1f°C", it.change) } ?: Format.INSUFFICIENT,
                            "最近 ${r.label}",
                            valueColor = when {
                                s == null -> MaterialTheme.colorScheme.onSurface
                                s.change > 0.05 -> status.warn
                                s.change < -0.05 -> status.good
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            dim = s == null,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                windows.forEach { (r, s) ->
                    if (s != null) {
                        Caption(
                            "${r.label}：" +
                                String.format(Locale.US, "最低 %.1f／最高 %.1f／平均 %.1f°C，", s.min, s.max, s.average) +
                                "${s.count} 筆（${Format.fullDateTime(s.firstTimestamp)} – ${Format.fullDateTime(s.lastTimestamp)}）",
                        )
                    }
                }
            }
        }
        item {
            StatRow {
                StatTile(
                    Modifier.weight(1f), "保存期間最高",
                    maxT?.let { Format.temperature(it.value / 10.0) } ?: "—",
                    valueColor = status.warn,
                ) { Caption(maxT?.let { Format.fullDateTime(it.timestamp) } ?: "尚無紀錄") }
                StatTile(
                    Modifier.weight(1f), "保存期間最低",
                    minT?.let { Format.temperature(it.value / 10.0) } ?: "—",
                    valueColor = MaterialTheme.colorScheme.secondary,
                ) { Caption(minT?.let { Format.fullDateTime(it.timestamp) } ?: "尚無紀錄") }
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "溫度趨勢（°C）") {
                TimeSeriesChart(
                    points = chart, start = start, end = now,
                    gapMillis = Series.gapThreshold(settings.sampleIntervalMinutes),
                    lineColor = MaterialTheme.colorScheme.tertiary, unit = "°C",
                    thresholds = listOf(
                        Threshold(settings.tempWarnC.toFloat(), status.warn),
                        Threshold(settings.tempHighC.toFloat(), status.danger),
                    ),
                    valueFormat = { String.format(Locale.US, "%.1f", it) },
                )
                ChartCoverage(chart, start, now)
            }
        }
        item {
            InfoNote(
                "這是 Android 回報的「電池」溫度，不是 CPU 或處理器溫度。「正常／偏高／高溫警告」只是依照您在設定中" +
                    "調整的門檻產生的提示，不是 Samsung 或 Android 的官方硬體診斷。充電、遊戲、導航或高溫環境下溫度上升屬於常見情況。",
            )
        }
    }
}
