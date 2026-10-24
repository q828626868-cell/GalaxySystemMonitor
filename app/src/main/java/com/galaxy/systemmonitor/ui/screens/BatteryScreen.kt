package com.galaxy.systemmonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.galaxy.systemmonitor.domain.RateStatus
import com.galaxy.systemmonitor.ui.components.DataSource
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.StatColumn
import com.galaxy.systemmonitor.ui.components.StatRow
import com.galaxy.systemmonitor.ui.components.StatTile
import com.galaxy.systemmonitor.ui.components.StatusChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.domain.PowerSession
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
    val power by vm.power.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.H24) }
    val status = LocalStatusColors.current

    val samples = history.orEmpty()
    val levelPoints = remember(samples) { Series.levelChart(samples) }
    val tempPoints = remember(samples) { Series.temperatureChart(samples) }
    val gap = Series.gapThreshold(settings.sampleIntervalMinutes)
    val start = now - range.millis

    ScreenScaffold(title = "電池", onBack = onBack) {
        val reading = live?.battery
        val battery = reading?.valueOrNull()
        if (battery == null) {
            item { SectionCard { UnavailableText(reading?.unavailableReason() ?: "讀取中…") } }
        } else {
            item { BatteryHero(battery, power.latest?.powerW) }
            item {
                val tempLevel = TemperatureAnalyzer.classify(battery.temperatureC, settings.tempWarnC, settings.tempHighC)
                StatRow {
                    StatTile(Modifier.weight(1f), "電池溫度", Format.temperature(battery.temperatureC), source = DataSource.MEASURED) {
                        StatusChip(tempLevel.label, status.forTemp(tempLevel))
                    }
                    StatTile(Modifier.weight(1f), "電壓", Format.voltage(battery.voltageMv), source = DataSource.MEASURED) {
                        Caption(battery.technology ?: "電池技術不明")
                    }
                }
            }
            item {
                val soh = battery.stateOfHealth.valueOrNull()
                StatRow {
                    StatTile(
                        Modifier.weight(1f), "電池健康度",
                        soh?.let { "$it%" } ?: "—",
                        source = DataSource.MEASURED,
                        valueColor = when {
                            soh == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            soh >= 80 -> status.good
                            soh >= 60 -> status.warn
                            else -> status.danger
                        },
                    ) {
                        if (soh != null) UsageBar(soh / 100f, status.good) else Caption(battery.stateOfHealth.unavailableReason().orEmpty())
                    }
                    StatTile(Modifier.weight(1f), "充電循環", battery.cycleCount?.let { "$it 次" } ?: "—", source = DataSource.MEASURED) {
                        Caption(if (battery.cycleCount == null) "裝置未提供" else "系統回報")
                    }
                }
            }
            item {
                SectionCard(title = "電池資訊", source = DataSource.MEASURED) {
                    MetricRow("充電狀態", battery.status.label)
                    MetricRow("電源", battery.plugType.label)
                    MetricRow("Android 健康狀態", battery.health.label)
                    live?.let { MetricRow("更新時間", Format.fullDateTime(it.timestamp)) }
                    battery.notes.forEach { Caption("• $it") }
                }
            }
        }
        item { PowerCard(power, status.good, MaterialTheme.colorScheme.tertiary) }
        item {
            SectionCard(title = "平均耗電速度（放電期間）", source = DataSource.DERIVED) {
                val d = drain
                if (d == null) {
                    UnavailableText("計算中…")
                } else {
                    RateSummary(listOf("1 小時" to d.discharge1h, "6 小時" to d.discharge6h, "24 小時" to d.discharge24h), charging = false)
                }
            }
        }
        item {
            SectionCard(title = "平均充電速度（充電期間）", source = DataSource.DERIVED) {
                val d = drain
                if (d == null) {
                    UnavailableText("計算中…")
                } else {
                    RateSummary(listOf("1 小時" to d.charge1h, "6 小時" to d.charge6h), charging = true)
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
            SectionCard(title = "電量（%）", source = DataSource.MEASURED) {
                TimeSeriesChart(
                    points = levelPoints, start = start, end = now, gapMillis = gap,
                    lineColor = MaterialTheme.colorScheme.primary, unit = "%",
                    fixedMin = 0f, fixedMax = 100f,
                )
                ChartCoverage(levelPoints, start, now)
            }
        }
        item {
            SectionCard(title = "電池溫度（°C）", source = DataSource.MEASURED) {
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
                "電池健康度（目前最大容量相對於出廠容量的百分比）來自 Android 14 的 BATTERY_PROPERTY_STATE_OF_HEALTH，" +
                    "需要 BATTERY_STATS 權限。此權限無法在 App 內申請，只能用電腦執行：\n" +
                    "adb shell pm grant com.galaxy.systemmonitor android.permission.BATTERY_STATS\n" +
                    "授權後重新開啟 App 即可；解除安裝 App 時權限會一併移除。若授權後仍顯示「裝置未提供」，" +
                    "代表此裝置的電池驅動未實作該數值，本 App 不會推測。上方的「Android 健康狀態」只是系統回報的概略分類。",
            )
        }
    }
}

@Composable
private fun PowerCard(session: PowerSession, chargeColor: Color, dischargeColor: Color) {
    SectionCard(title = "即時功率（每 2 秒更新）", source = DataSource.DERIVED) {
        val p = session.latest
        if (p == null) {
            UnavailableText("讀取中…")
            return@SectionCard
        }
        val w = p.powerW
        val color = when {
            w == null -> MaterialTheme.colorScheme.onSurface
            w > 0 -> chargeColor
            else -> dischargeColor
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigValue(Format.watts(w), color)
            Spacer(Modifier.weight(1f))
            StatusChip(
                when {
                    w == null -> "無法計算"
                    w > 0.05 -> "充入電池"
                    w < -0.05 -> "電池放電"
                    else -> "幾乎無電流"
                },
                color,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            StatColumn(Modifier.weight(1f), Format.milliamps(p.currentMa), "即時電流", source = DataSource.MEASURED)
            StatColumn(Modifier.weight(1f), Format.voltage(p.voltageMv), "電壓", source = DataSource.MEASURED)
            StatColumn(
                Modifier.weight(1f),
                p.chargeCounterMah?.let { "$it mAh" } ?: "—",
                "剩餘電量",
                source = DataSource.MEASURED,
                dim = p.chargeCounterMah == null,
            )
        }
        MetricRow("平均電流（系統計算）", p.averageCurrentMa?.let { Format.milliamps(it) } ?: "裝置未提供")
        if (session.peakChargeW != null || session.peakDischargeW != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.peakChargeW?.let { StatusChip("最高充電 ${Format.watts(it)}", chargeColor) }
                session.peakDischargeW?.let { StatusChip("最高放電 ${Format.watts(-it)}", dischargeColor) }
            }
        }
        p.notes.forEach { Caption("• $it") }
        if (session.points.size >= 2) {
            val end = session.points.last().timestamp
            TimeSeriesChart(
                points = session.points,
                start = maxOf(session.points.first().timestamp, end - PowerSession.WINDOW_MILLIS),
                end = end,
                gapMillis = 10_000,
                lineColor = if ((w ?: 0.0) >= 0) chargeColor else dischargeColor,
                unit = " W",
                height = 140.dp,
                valueFormat = { String.format(Locale.US, "%.1f", it) },
            )
            Text(
                "只保留開著此畫面期間最近 10 分鐘的數值，不寫入歷史紀錄。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoNote(
            "功率＝電壓 × 電流，是「進出電池」的淨功率：充電時＝充電器輸出－手機本身耗電，" +
                "所以會比充電器標示的瓦數低，邊充邊用時甚至可能是負的。電池充到約 80% 後系統會主動降低電流，這是正常的。",
        )
    }
}

/** Big figures side by side, then one line per window explaining what data it is based on. */
@Composable
private fun RateSummary(windows: List<Pair<String, RateResult>>, charging: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        windows.forEach { (label, r) ->
            StatColumn(Modifier.weight(1f), Format.rateShort(r, charging), "最近 $label", dim = r.status != RateStatus.OK)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    windows.forEach { (label, r) ->
        val detail = if (r.firstTimestamp != null && r.lastTimestamp != null) {
            "依據 ${Format.duration(r.coveredMillis)} 的紀錄（${Format.fullDateTime(r.firstTimestamp)} – ${Format.fullDateTime(r.lastTimestamp)}），" +
                "共${if (charging) "上升" else "下降"} ${r.totalPoints} 個百分點"
        } else {
            "沒有可用的${if (charging) "充電" else "放電"}紀錄"
        }
        Caption("$label：$detail")
    }
}
