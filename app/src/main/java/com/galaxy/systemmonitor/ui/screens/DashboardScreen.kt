package com.galaxy.systemmonitor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.ChargeStatus
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.domain.DrainSummary
import com.galaxy.systemmonitor.domain.RateStatus
import com.galaxy.systemmonitor.domain.TemperatureAnalyzer
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.HeroCard
import com.galaxy.systemmonitor.ui.components.HeroLabel
import com.galaxy.systemmonitor.ui.components.HeroPill
import com.galaxy.systemmonitor.ui.components.HeroPills
import com.galaxy.systemmonitor.ui.components.HeroSubtitle
import com.galaxy.systemmonitor.ui.components.HeroValue
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.RingGauge
import com.galaxy.systemmonitor.ui.components.StatColumn
import com.galaxy.systemmonitor.ui.components.StatRow
import com.galaxy.systemmonitor.ui.components.StatTile
import com.galaxy.systemmonitor.ui.components.StatusDot
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
    val lastScheduled by vm.lastScheduledSample.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val power by vm.power.collectAsStateWithLifecycle()
    val device by vm.deviceInfo.collectAsStateWithLifecycle()
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
        item {
            val reading = snap?.battery
            val battery = reading?.valueOrNull()
            if (battery == null) {
                SectionCard(title = "電池", onClick = { onOpen(Destinations.BATTERY) }) {
                    UnavailableText(reading?.unavailableReason() ?: "讀取中…")
                }
            } else {
                BatteryHero(battery, power.latest?.powerW) {
                    onOpen(Destinations.BATTERY)
                }
            }
        }
        item { DrainCard(drain) { onOpen(Destinations.BATTERY) } }

        // 2 × 2 quick stats
        item {
            val battery = snap?.battery?.valueOrNull()
            val memory = snap?.memory?.valueOrNull()
            val storage = snap?.storage?.valueOrNull()
            val net = snap?.network?.valueOrNull()
            val tempLevel = TemperatureAnalyzer.classify(battery?.temperatureC, settings.tempWarnC, settings.tempHighC)
            val lowStorage = storage != null && storage.freeFraction * 100 < settings.lowStoragePercent
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow {
                    StatTile(
                        Modifier.weight(1f), "電池溫度", Format.temperature(battery?.temperatureC),
                        onClick = { onOpen(Destinations.TEMPERATURE) },
                    ) { StatusChip(tempLevel.label, status.forTemp(tempLevel)) }
                    StatTile(
                        Modifier.weight(1f), "記憶體",
                        memory?.let { Format.percent(it.usedFraction) } ?: Format.UNAVAILABLE,
                        onClick = { onOpen(Destinations.MEMORY) },
                    ) {
                        if (memory != null) {
                            UsageBar(memory.usedFraction.toFloat(), MaterialTheme.colorScheme.secondary)
                            Caption("可用 ${Format.bytes(memory.availableBytes)}")
                        } else {
                            Caption(snap?.memory?.unavailableReason() ?: "讀取中…")
                        }
                    }
                }
                StatRow {
                    StatTile(
                        Modifier.weight(1f), "儲存空間剩餘",
                        storage?.let { Format.bytes(it.freeBytes) } ?: Format.UNAVAILABLE,
                        valueColor = if (lowStorage) status.warn else MaterialTheme.colorScheme.onSurface,
                        onClick = { onOpen(Destinations.STORAGE) },
                    ) {
                        if (storage != null) {
                            UsageBar(storage.usedFraction.toFloat(), if (lowStorage) status.warn else MaterialTheme.colorScheme.primary)
                            Caption(if (lowStorage) "剩餘空間偏低" else "共 ${Format.bytes(storage.totalBytes)}")
                        } else {
                            Caption(snap?.storage?.unavailableReason() ?: "讀取中…")
                        }
                    }
                    StatTile(
                        Modifier.weight(1f), "網路",
                        net?.let { if (it.connected) it.transport.label else "無連線" } ?: Format.UNAVAILABLE,
                        onClick = { onOpen(Destinations.NETWORK) },
                    ) {
                        if (net != null) {
                            val ok = net.connected && net.validated == true
                            StatusDot(
                                when {
                                    !net.connected -> "沒有預設網路"
                                    net.validated == true -> "可上網"
                                    net.validated == false -> "未驗證"
                                    else -> "狀態未知"
                                },
                                if (ok) status.good else status.warn,
                            )
                        } else {
                            Caption(snap?.network?.unavailableReason() ?: "讀取中…")
                        }
                    }
                }
            }
        }

        // More screens
        item {
            SectionCard {
                NavRow(
                    Icons.Filled.Phone, MaterialTheme.colorScheme.primary, "裝置資訊",
                    device?.let { "${it.marketName ?: it.model} · ${it.socName ?: it.socModel ?: "處理器未知"}" }
                        ?: "型號、系統版本、處理器、螢幕",
                ) { onOpen(Destinations.DEVICE) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(
                    Icons.Filled.DateRange, MaterialTheme.colorScheme.tertiary, "App 使用情況",
                    "各 App 的使用時間與網路流量",
                ) { onOpen(Destinations.APP_USAGE) }
            }
        }
    }
}

/** Battery headline card, shared by the dashboard and the battery screen. */
@Composable
internal fun BatteryHero(battery: BatteryInfo, powerW: Double?, onClick: (() -> Unit)? = null) {
    val status = LocalStatusColors.current
    val level = battery.levelPercent
    val ringColor = when {
        level == null -> status.muted
        battery.status == ChargeStatus.CHARGING -> status.good
        level <= 15 -> status.danger
        level <= 30 -> status.warn
        else -> MaterialTheme.colorScheme.primary
    }
    HeroCard(onClick = onClick, trailing = { RingGauge(level?.let { it / 100f }, ringColor) }) {
        HeroLabel("目前電量")
        HeroValue(level?.let { "$it%" } ?: Format.UNAVAILABLE)
        HeroSubtitle(
            if (battery.pluggedIn == true) "${battery.status.label} · ${battery.plugType.label}" else battery.status.label,
        )
        HeroPills {
            powerW?.let { HeroPill(Format.watts(it), if (it > 0) status.good else MaterialTheme.colorScheme.onPrimaryContainer) }
            battery.stateOfHealth.valueOrNull()?.let { HeroPill("健康度 $it%") }
        }
    }
}

@Composable
private fun DrainCard(drain: DrainSummary?, onClick: () -> Unit) {
    SectionCard(title = "平均耗電速度（放電期間）", onClick = onClick) {
        if (drain == null) {
            UnavailableText("計算中…")
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("1 小時" to drain.discharge1h, "6 小時" to drain.discharge6h, "24 小時" to drain.discharge24h)
                .forEach { (label, rate) ->
                    StatColumn(Modifier.weight(1f), Format.rateShort(rate), "最近 $label", dim = rate.status != RateStatus.OK)
                }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
