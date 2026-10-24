package com.galaxy.systemmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.galaxy.systemmonitor.ui.components.DataSource
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.StatColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.data.db.chargeStatus
import com.galaxy.systemmonitor.data.db.memoryUsedFraction
import com.galaxy.systemmonitor.data.db.temperatureC
import com.galaxy.systemmonitor.data.model.NetworkTransport
import com.galaxy.systemmonitor.domain.Series
import com.galaxy.systemmonitor.domain.TimeRange
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.ChartCoverage
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.RangeSelector
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.TimeSeriesChart
import com.galaxy.systemmonitor.util.Format
import java.util.Locale

@Composable
fun HistoryScreen(vm: MonitorViewModel, onBack: (() -> Unit)?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val recent by vm.recentSamples.collectAsStateWithLifecycle()
    val count by vm.sampleCount.collectAsStateWithLifecycle()
    val oldest by vm.oldestSample.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.H24) }

    val samples = history.orEmpty()
    val level = remember(samples) { Series.levelChart(samples) }
    val temp = remember(samples) { Series.temperatureChart(samples) }
    val memory = remember(samples) { Series.memoryChart(samples) }
    val gap = Series.gapThreshold(settings.sampleIntervalMinutes)
    val start = now - range.millis

    ScreenScaffold(title = "歷史紀錄", onBack = onBack) {
        item {
            SectionCard(title = "本機資料") {
                Row(Modifier.fillMaxWidth()) {
                    StatColumn(Modifier.weight(1f), "$count", "紀錄筆數")
                    StatColumn(Modifier.weight(1f), "${settings.retentionDays} 天", "保存期限")
                    StatColumn(Modifier.weight(1f), oldest?.let { Format.dateTime(it) } ?: "—", "最早紀錄", dim = oldest == null)
                }
            }
        }
        if (history != null && samples.isEmpty()) {
            item {
                InfoNote("尚無歷史資料。App 會在開啟時、背景排程執行時及按下重新整理時記錄，累積一段時間後即可看到趨勢。")
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "電量（%）", source = DataSource.MEASURED) {
                TimeSeriesChart(level, start, now, gap, MaterialTheme.colorScheme.primary, "%", fixedMin = 0f, fixedMax = 100f)
                ChartCoverage(level, start, now)
            }
        }
        item {
            SectionCard(title = "電池溫度（°C）", source = DataSource.MEASURED) {
                TimeSeriesChart(
                    temp, start, now, gap, MaterialTheme.colorScheme.tertiary, "°C",
                    valueFormat = { String.format(Locale.US, "%.1f", it) },
                )
                ChartCoverage(temp, start, now)
            }
        }
        item {
            SectionCard(title = "記憶體使用比例（%）", source = DataSource.ESTIMATED) {
                TimeSeriesChart(memory, start, now, gap, MaterialTheme.colorScheme.secondary, "%", fixedMin = 0f, fixedMax = 100f)
                ChartCoverage(memory, start, now)
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Text(
                    "最近 ${recent.size} 筆紀錄",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(recent, key = { it.id }) { s ->
                val level = s.battery?.level
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Battery level badge
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            level?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Format.fullDateTime(s.timestamp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Caption(SampleSource.entries.firstOrNull { it.name == s.source }?.label ?: s.source)
                        }
                        Caption(
                            listOf(
                                s.chargeStatus.label,
                                Format.temperature(s.temperatureC),
                                s.memoryUsedFraction?.let { "RAM ${Format.percent(it)}" } ?: "RAM 不可用",
                                when (s.network?.connected) {
                                    true -> NetworkTransport.fromName(s.network.transport).label
                                    false -> "無連線"
                                    null -> "網路未知"
                                },
                            ).joinToString(" · "),
                        )
                    }
                }
            }
        }
    }
}
