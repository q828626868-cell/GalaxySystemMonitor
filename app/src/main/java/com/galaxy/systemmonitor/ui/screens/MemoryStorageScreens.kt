package com.galaxy.systemmonitor.ui.screens

import androidx.compose.material3.MaterialTheme
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
    val start = now - range.millis

    ScreenScaffold(title = "系統記憶體", onBack = onBack) {
        item {
            val reading = live?.memory
            val m = reading?.valueOrNull()
            SectionCard(title = "目前狀態（整台裝置）") {
                if (m == null) {
                    UnavailableText(reading?.unavailableReason() ?: "讀取中…")
                } else {
                    BigValue(Format.percent(m.usedFraction))
                    UsageBar(m.usedFraction.toFloat(), MaterialTheme.colorScheme.secondary)
                    MetricRow("系統可用總記憶體", Format.bytes(m.totalBytes))
                    MetricRow("可用記憶體", Format.bytes(m.availableBytes))
                    MetricRow("使用中（估算）", Format.bytes(m.usedBytes))
                    MetricRow("系統低記憶體門檻", m.lowMemoryThresholdBytes?.let { Format.bytes(it) } ?: Format.UNAVAILABLE)
                    MetricRow("系統處於低記憶體狀態", if (m.lowMemory) "是" else "否")
                }
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "記憶體使用比例（%）") {
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
        item {
            SectionCard(title = "內部儲存空間") {
                if (s == null) {
                    UnavailableText(reading?.unavailableReason() ?: "讀取中…")
                } else {
                    val low = s.freeFraction * 100 < settings.lowStoragePercent
                    BigValue(Format.bytes(s.freeBytes), if (low) status.warn else MaterialTheme.colorScheme.onSurface)
                    UsageBar(s.usedFraction.toFloat(), if (low) status.warn else MaterialTheme.colorScheme.primary)
                    MetricRow("總容量", Format.bytes(s.totalBytes))
                    MetricRow("已使用（含系統）", Format.bytes(s.usedBytes))
                    MetricRow("剩餘", Format.bytes(s.freeBytes))
                    MetricRow("使用比例", Format.percent(s.usedFraction))
                    MetricRow("資料來源", s.source.label)
                }
            }
        }
        item { RangeSelector(range, { range = it }) }
        item {
            SectionCard(title = "剩餘空間（GB）") {
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
                    "取得每個 App 的佔用量需要「使用情況存取權」，本 App 刻意不要求此權限，也不提供分類明細或刪除功能。",
            )
        }
    }
}
