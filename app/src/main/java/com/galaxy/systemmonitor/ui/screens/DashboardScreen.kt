package com.galaxy.systemmonitor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.domain.TemperatureAnalyzer
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.BigValue
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.StatusChip
import com.galaxy.systemmonitor.ui.components.UnavailableText
import com.galaxy.systemmonitor.ui.components.UsageBar
import com.galaxy.systemmonitor.ui.components.WarningBanner
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format

@Composable
fun DashboardScreen(vm: MonitorViewModel, onOpen: (String) -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drain by vm.drain.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val dbError by vm.databaseError.collectAsStateWithLifecycle()
    val count by vm.sampleCount.collectAsStateWithLifecycle()
    val oldest by vm.oldestSample.collectAsStateWithLifecycle()
    val lastScheduled by vm.lastScheduledSample.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current

    ScreenScaffold(
        title = "Galaxy System Monitor",
        onBack = null,
        actions = {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = { vm.refresh(manual = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新整理")
                }
            }
        },
    ) {
        item {
            Text(
                live?.let { "最後更新 ${Format.dateTime(it.timestamp)}（${Format.relative(it.timestamp, now)}）" }
                    ?: "讀取中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        dbError?.let { item { WarningBanner(it, status.danger) } }
        if (!settings.backgroundMonitoring) {
            item { InfoNote("背景監控已關閉：只有開啟 App、使用 Widget 重新整理時才會記錄資料。") }
        } else {
            val staleLimit = maxOf(3L * settings.sampleIntervalMinutes * 60_000, 2 * 3_600_000L)
            val ls = lastScheduled
            if (ls != null && now - ls > staleLimit) {
                item {
                    WarningBanner(
                        "背景取樣已 ${Format.relative(ls, now).removeSuffix("前")}未執行，系統可能限制了本 App 的背景活動。" +
                            "可至「設定」查看說明。",
                    )
                }
            }
        }

        val snap = live
        // Battery
        item {
            val battery = snap?.battery?.valueOrNull()
            SectionCard(title = "電池", onClick = { onOpen(Destinations.BATTERY) }) {
                if (battery == null) {
                    UnavailableText(snap?.battery?.unavailableReason() ?: "讀取中…")
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigValue(battery.levelPercent?.let { "$it%" } ?: Format.UNAVAILABLE)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (battery.pluggedIn == true) "${battery.status.label} · ${battery.plugType.label}"
                                else battery.status.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val level = TemperatureAnalyzer.classify(battery.temperatureC, settings.tempWarnC, settings.tempHighC)
                            Text(
                                "電池溫度 ${Format.temperature(battery.temperatureC)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = status.forTemp(level),
                            )
                        }
                    }
                    battery.levelPercent?.let { UsageBar(it / 100f) }
                }
            }
        }
        // Drain
        item {
            SectionCard(title = "近期平均耗電速度（電量百分點／小時）", onClick = { onOpen(Destinations.BATTERY) }) {
                val d = drain
                if (d == null) {
                    UnavailableText("計算中…")
                } else {
                    MetricRow("最近 1 小時", Format.rateShort(d.discharge1h))
                    MetricRow("最近 6 小時", Format.rateShort(d.discharge6h))
                    MetricRow("最近 24 小時", Format.rateShort(d.discharge24h))
                    Text(
                        "僅以實際記錄的放電期間計算，不含充電期間。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Temperature + memory
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val battery = snap?.battery?.valueOrNull()
                val level = TemperatureAnalyzer.classify(battery?.temperatureC, settings.tempWarnC, settings.tempHighC)
                SectionCard(Modifier.weight(1f), title = "電池溫度", onClick = { onOpen(Destinations.TEMPERATURE) }) {
                    Text(
                        Format.temperature(battery?.temperatureC),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusChip(level.label, status.forTemp(level))
                }
                val memory = snap?.memory?.valueOrNull()
                SectionCard(Modifier.weight(1f), title = "系統記憶體", onClick = { onOpen(Destinations.MEMORY) }) {
                    if (memory == null) {
                        UnavailableText(snap?.memory?.unavailableReason())
                    } else {
                        Text(
                            Format.percent(memory.usedFraction),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        UsageBar(memory.usedFraction.toFloat(), MaterialTheme.colorScheme.secondary)
                        Text(
                            "可用 ${Format.bytes(memory.availableBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Storage + network
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val storage = snap?.storage?.valueOrNull()
                SectionCard(Modifier.weight(1f), title = "儲存空間", onClick = { onOpen(Destinations.STORAGE) }) {
                    if (storage == null) {
                        UnavailableText(snap?.storage?.unavailableReason())
                    } else {
                        val low = storage.freeFraction * 100 < settings.lowStoragePercent
                        Text(
                            Format.bytes(storage.freeBytes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (low) status.warn else MaterialTheme.colorScheme.onSurface,
                        )
                        UsageBar(storage.usedFraction.toFloat(), if (low) status.warn else MaterialTheme.colorScheme.primary)
                        Text(
                            if (low) "剩餘空間偏低" else "剩餘 · 共 ${Format.bytes(storage.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val net = snap?.network?.valueOrNull()
                SectionCard(Modifier.weight(1f), title = "網路", onClick = { onOpen(Destinations.NETWORK) }) {
                    if (net == null) {
                        UnavailableText(snap?.network?.unavailableReason())
                    } else {
                        Text(
                            if (net.connected) net.transport.label else "無連線",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                !net.connected -> "沒有預設網路"
                                net.validated == true -> "系統判定可上網"
                                net.validated == false -> "網際網路未驗證"
                                else -> "狀態未知"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (net.connected && net.validated == true) status.good else status.warn,
                        )
                    }
                }
            }
        }
        // History
        item {
            SectionCard(title = "歷史紀錄", onClick = { onOpen(Destinations.HISTORY) }) {
                MetricRow("本機紀錄筆數", "$count")
                MetricRow("最早紀錄", oldest?.let { Format.fullDateTime(it) } ?: "尚無紀錄")
                MetricRow("保存期限", "${settings.retentionDays} 天")
            }
        }
    }
}
