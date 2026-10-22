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
            SectionCard(title = "目前電池溫度") {
                BigValue(Format.temperature(battery?.temperatureC), status.forTemp(level))
                StatusChip(level.label, status.forTemp(level))
                MetricRow("偏高門檻", "${settings.tempWarnC}°C")
                MetricRow("高溫警告門檻", "${settings.tempHighC}°C")
                live?.let { MetricRow("更新時間", Format.fullDateTime(it.timestamp)) }
            }
        }
        item {
            SectionCard(title = "近期變化") {
                listOf(TimeRange.H1, TimeRange.H6, TimeRange.H24).forEach { r ->
                    val s = TemperatureAnalyzer.windowStats(points, now, r.millis)
                    if (s == null) {
                        MetricRow("最近 ${r.label}", Format.INSUFFICIENT)
                    } else {
                        MetricRow(
                            "最近 ${r.label}",
                            String.format(Locale.US, "%+.1f°C", s.change),
                        )
                        Text(
                            String.format(Locale.US, "最低 %.1f / 最高 %.1f / 平均 %.1f°C · ", s.min, s.max, s.average) +
                                "${s.count} 筆（${Format.fullDateTime(s.firstTimestamp)} – ${Format.fullDateTime(s.lastTimestamp)}）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = "保存期間內的最高／最低紀錄") {
                MetricRow(
                    "最高",
                    maxT?.let { "${Format.temperature(it.value / 10.0)}（${Format.fullDateTime(it.timestamp)}）" } ?: "尚無紀錄",
                )
                MetricRow(
                    "最低",
                    minT?.let { "${Format.temperature(it.value / 10.0)}（${Format.fullDateTime(it.timestamp)}）" } ?: "尚無紀錄",
                )
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
