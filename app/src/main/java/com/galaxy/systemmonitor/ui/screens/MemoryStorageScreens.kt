package com.galaxy.systemmonitor.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.galaxy.systemmonitor.ui.components.DataSource
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.HeroCard
import com.galaxy.systemmonitor.ui.components.HeroLabel
import com.galaxy.systemmonitor.ui.components.HeroPill
import com.galaxy.systemmonitor.ui.components.HeroPills
import com.galaxy.systemmonitor.ui.components.HeroSubtitle
import com.galaxy.systemmonitor.ui.components.HeroValue
import com.galaxy.systemmonitor.ui.components.RingGauge
import com.galaxy.systemmonitor.ui.components.StatRow
import com.galaxy.systemmonitor.ui.components.StatTile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.domain.Series
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
import com.galaxy.systemmonitor.ui.components.WarningBanner
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format
import java.util.Locale

@Composable
fun MemoryScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.H24) }
    val samples = history.orEmpty()
    val chart = remember(samples) { Series.memoryChart(samples) }
    val status = LocalStatusColors.current
    val start = now - range.millis

    ScreenScaffold(title = "系統記憶體", onBack = onBack) {
        val reading = live?.memory
        val m = reading?.valueOrNull()
        if (m == null) {
            item { SectionCard { UnavailableText(reading?.unavailableReason() ?: "讀取中…") } }
        } else {
            item {
                HeroCard(
                    trailing = {
                        RingGauge(m.usedFraction.toFloat(), MaterialTheme.colorScheme.secondary, centerText = Format.percent(m.usedFraction))
                    },
                ) {
                    HeroLabel("系統記憶體使用中", DataSource.ESTIMATED)
                    HeroValue(Format.bytes(m.usedBytes), large = false)
                    HeroSubtitle("共 ${Format.bytes(m.totalBytes)}（整台裝置）")
                    HeroPills {
                        HeroPill(
                            if (m.lowMemory) "低記憶體狀態" else "記憶體充足",
                            if (m.lowMemory) status.danger else status.good,
                        )
                    }
                }
            }
            item {
                StatRow {
                    StatTile(Modifier.weight(1f), "可用", Format.bytes(m.availableBytes), source = DataSource.MEASURED) {
                        UsageBar((1 - m.usedFraction).toFloat(), status.good)
                    }
                    StatTile(
                        Modifier.weight(1f), "低記憶體門檻",
                        m.lowMemoryThresholdBytes?.let { Format.bytes(it) } ?: Format.UNAVAILABLE,
                        source = DataSource.MEASURED,
                    ) { Caption("可用量低於此值時系統開始回收") }
                }
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "記憶體使用比例（%）", source = DataSource.ESTIMATED) {
                TimeSeriesChart(
                    points = chart, start = start, end = now,
                    gapMillis = Series.gapThreshold(settings.sampleIntervalMinutes),
                    lineColor = MaterialTheme.colorScheme.secondary, unit = "%",
                    fixedMin = 0f, fixedMax = 100f,
                )
                ChartCoverage(chart, start, now)
            }
        }
        item {
            InfoNote(
                "數值來自 Android ActivityManager.getMemoryInfo()，代表整台裝置而非本 App。" +
                    "「系統可用總記憶體」不含核心與硬體保留的部分，因此會小於規格上的 12 GB；" +
                    "「可用記憶體」包含系統可隨時回收的快取，所以「使用中」只是總量減去可用量的估算。" +
                    "Android 會主動利用空閒記憶體做快取，使用比例偏高通常是正常現象。" +
                    "一般 App 無法取得其他 App 的即時記憶體明細，本 App 也不提供「清理記憶體」或強制關閉其他 App 的功能。",
            )
        }
    }
}

@Composable
fun StorageScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.D7) }
    val samples = history.orEmpty()
    val chart = remember(samples) { Series.storageFreeGbChart(samples) }
    val start = now - range.millis
    val status = LocalStatusColors.current

    ScreenScaffold(title = "儲存空間", onBack = onBack) {
        val reading = live?.storage
        val s = reading?.valueOrNull()
        if (s != null && s.freeFraction * 100 < settings.lowStoragePercent) {
            item {
                WarningBanner("剩餘空間低於 ${settings.lowStoragePercent}%（可於設定調整門檻）。本 App 不會刪除任何檔案，請自行於系統設定管理。")
            }
        }
        if (s == null) {
            item { SectionCard { UnavailableText(reading?.unavailableReason() ?: "讀取中…") } }
        } else {
            val low = s.freeFraction * 100 < settings.lowStoragePercent
            item {
                val accent = if (low) status.warn else MaterialTheme.colorScheme.primary
                HeroCard(trailing = { RingGauge(s.usedFraction.toFloat(), accent, centerText = Format.percent(s.usedFraction)) }) {
                    HeroLabel("內部儲存空間剩餘", DataSource.MEASURED)
                    HeroValue(Format.bytes(s.freeBytes), if (low) status.warn else MaterialTheme.colorScheme.onPrimaryContainer, large = false)
                    HeroSubtitle("共 ${Format.bytes(s.totalBytes)}")
                    HeroPills { HeroPill(if (low) "空間偏低" else "空間充足", if (low) status.warn else status.good) }
                    Caption("來源：${s.source.label}", MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
            item {
                val accent = if (low) status.warn else MaterialTheme.colorScheme.primary
                StatRow {
                    StatTile(Modifier.weight(1f), "已使用（含系統）", Format.bytes(s.usedBytes), source = DataSource.DERIVED) {
                        UsageBar(s.usedFraction.toFloat(), accent)
                    }
                    StatTile(Modifier.weight(1f), "剩餘比例", Format.percent(s.freeFraction), source = DataSource.DERIVED) {
                        UsageBar(s.freeFraction.toFloat(), status.good)
                    }
                }
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "剩餘空間（GB）", source = DataSource.MEASURED) {
                TimeSeriesChart(
                    points = chart, start = start, end = now,
                    gapMillis = Series.gapThreshold(settings.sampleIntervalMinutes),
                    lineColor = MaterialTheme.colorScheme.primary, unit = " GB",
                    valueFormat = { String.format(Locale.US, "%.1f", it) },
                )
                ChartCoverage(chart, start, now)
            }
        }
        item {
            InfoNote(
                "總容量與剩餘空間取自 Android StorageStatsManager（與系統設定顯示一致，以 1 GB = 10⁹ bytes 計）。" +
                    "「已使用」是總容量減去剩餘空間，包含 Android 系統本身，因此不等於照片、App 等分類加總。" +
                    "本 App 不提供各 App 佔用量的分類明細，也沒有刪除檔案的功能。",
            )
        }
    }
}
