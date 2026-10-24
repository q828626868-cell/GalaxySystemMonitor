package com.galaxy.systemmonitor.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.model.DeviceInfo
import com.galaxy.systemmonitor.data.model.DeviceLive
import com.galaxy.systemmonitor.data.model.ThermalStatus
import com.galaxy.systemmonitor.domain.CpuTopology
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.DataSource
import com.galaxy.systemmonitor.ui.components.SourceTag
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.StatusChip
import com.galaxy.systemmonitor.ui.components.UnavailableText
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format
import java.util.Locale
import kotlin.math.roundToInt

private const val NOT_PROVIDED = "裝置未提供"

private fun ghz(mhz: Int?): String = mhz?.let { String.format(Locale.US, "%.2f GHz", it / 1000.0) } ?: NOT_PROVIDED
private fun hz(v: Float): String = "${v.roundToInt()} Hz"

private fun clusterName(index: Int, count: Int): String = when {
    count == 1 -> "核心"
    count == 2 -> if (index == 0) "大核" else "小核"
    else -> when (index) {
        0 -> "超大核"
        count - 1 -> "小核"
        else -> "大核"
    }
}

@Composable
fun DeviceScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val info by vm.deviceInfo.collectAsStateWithLifecycle()
    val live by vm.deviceLive.collectAsStateWithLifecycle()

    ScreenScaffold(title = "裝置資訊", onBack = onBack) {
        val d = info
        if (d == null) {
            item { SectionCard { UnavailableText("讀取中…") } }
            return@ScreenScaffold
        }
        item { HeroCard(d) }
        item { SpecGrid(d, live) }
        item { CpuLiveCard(d, live) }
        item { ThermalCard(live) }
        item { DetailsCard(d) }
        item {
            InfoNote(
                "商品名稱與晶片名稱由 App 內建的對照表推得（Android 只提供型號代碼），不在表中的裝置只顯示原始代碼。" +
                    "CPU 即時頻率來自 Linux cpufreq，部分裝置不允許一般 App 讀取；Android 8 起一般 App 也無法讀取 CPU 使用率。",
            )
        }
    }
}

@Composable
private fun HeroCard(d: DeviceInfo) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            d.manufacturer.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        )
        // The marketing name comes from the app's table; the model code below is what the system reports.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                d.marketName ?: d.model,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            SourceTag(if (d.marketName != null) DataSource.LOOKUP else DataSource.MEASURED)
        }
        if (d.marketName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.model, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer.copy(alpha = 0.7f))
                SourceTag(DataSource.MEASURED)
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Android ${d.androidRelease}")
            d.oneUiVersion?.let { Pill("One UI $it") }
            d.securityPatch?.let { Pill("修補 $it") }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun SpecGrid(d: DeviceInfo, live: DeviceLive?) {
    val fastest = d.clusters.firstOrNull()?.maxMhz
    data class Spec(val label: String, val value: String, val sub: String, val source: DataSource)
    val tiles = listOf(
        Spec(
            "處理器", d.socName ?: d.socModel ?: NOT_PROVIDED, "${d.coreCount} 核 · 最高 ${ghz(fastest)}",
            if (d.socName != null) DataSource.LOOKUP else DataSource.MEASURED,
        ),
        Spec("GPU", d.gpuRenderer ?: NOT_PROVIDED, d.vulkanVersion?.let { "Vulkan $it" } ?: "Vulkan 不支援", DataSource.MEASURED),
        Spec(
            "螢幕",
            d.display?.let { "${it.widthPx} × ${it.heightPx}" } ?: NOT_PROVIDED,
            d.display?.let { "${hz(it.supportedRefreshHz.maxOrNull() ?: it.currentRefreshHz)} · ${it.densityDpi} dpi" } ?: "",
            DataSource.MEASURED,
        ),
        Spec(
            "已開機",
            live?.let { Format.duration(it.elapsedRealtimeMillis) } ?: "讀取中…",
            live?.let { "深度睡眠 ${Format.percent(it.deepSleepFraction)}（計算）" } ?: "",
            DataSource.MEASURED,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value, sub, source) ->
                    SectionCard(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            SourceTag(source)
                        }
                        Text(
                            value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuLiveCard(d: DeviceInfo, live: DeviceLive?) {
    SectionCard(title = "CPU 即時頻率（GHz）", source = DataSource.MEASURED) {
        CpuBars(d, live)
    }
}

/**
 * Cluster legend plus one animated bar per core. Shared by the device screen and the dashboard;
 * emits its rows into the caller's column.
 */
@Composable
internal fun CpuBars(d: DeviceInfo, live: DeviceLive?, barHeight: Dp = 150.dp, footnote: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    val clusterOf: Map<Int, Int> = d.clusters.flatMapIndexed { i, c -> c.cores.map { it to i } }.toMap()
    val maxOf: Map<Int, Int?> = d.clusters.flatMap { c -> c.cores.map { it to c.maxMhz } }.toMap()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        d.clusters.forEachIndexed { i, c -> ClusterLegend(c, clusterName(i, d.clusters.size), palette[i % palette.size]) }
    }
    Spacer(Modifier.height(8.dp))
    when {
        live == null -> UnavailableText("讀取中…")
        !live.coreFrequencyReadable -> UnavailableText("此裝置不允許一般 App 讀取 CPU 即時頻率")
        else -> {
            Row(
                Modifier.fillMaxWidth().height(barHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                live.coreMhz.toSortedMap().forEach { (core, cur) ->
                    val max = maxOf[core]
                    CoreBar(
                        core = core,
                        mhz = cur,
                        fraction = if (cur != null && max != null && max > 0) cur.toFloat() / max else 0f,
                        color = palette[(clusterOf[core] ?: 0) % palette.size],
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (footnote) {
                Text(
                    "柱高＝目前頻率 ÷ 該核心最高頻率（不是使用率），每 1.5 秒更新。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClusterLegend(c: CpuTopology.Cluster, name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            "$name ${c.cores.size} × ${ghz(c.maxMhz)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoreBar(core: Int, mhz: Int?, fraction: Float, color: Color, modifier: Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "core$core")
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            mhz?.let { String.format(Locale.US, "%.1f", it / 1000.0) } ?: "–",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animated.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)))),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("$core", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThermalCard(live: DeviceLive?) {
    val status = LocalStatusColors.current
    SectionCard(title = "散熱", source = DataSource.PREDICTED) {
        if (live == null) {
            UnavailableText("讀取中…")
            return@SectionCard
        }
        val color = when (live.thermalStatus) {
            ThermalStatus.NONE -> status.good
            ThermalStatus.LIGHT, ThermalStatus.MODERATE -> status.warn
            ThermalStatus.UNKNOWN -> status.muted
            else -> status.danger
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                live.thermalHeadroom?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StatusChip(live.thermalStatus.label, color)
        }
        val headroom = live.thermalHeadroom
        if (headroom != null) {
            val animated by animateFloatAsState(headroom.coerceIn(0f, 1f), tween(600), label = "headroom")
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(status.good, status.warn, status.danger))),
                )
            }
        }
        Text(
            if (headroom != null) "熱負載：系統預測 10 秒後的散熱壓力，到 100% 會開始嚴重降頻。" else "此裝置未提供熱負載預測。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailsCard(d: DeviceInfo) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "詳細資料",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收合" else "展開",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            val s = d.display
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Detail("裝置名稱", d.deviceName ?: NOT_PROVIDED)
                    Detail("Android", "${d.androidRelease}（API ${d.sdkInt}）")
                    Detail("版本號碼", d.buildNumber)
                    Detail("基頻版本", d.baseband ?: NOT_PROVIDED)
                    Detail("核心版本", d.kernel ?: NOT_PROVIDED)
                    Detail("指令集", d.abis.joinToString(", ").ifEmpty { NOT_PROVIDED })
                    Detail("晶片代碼", listOfNotNull(d.socManufacturer, d.socModel).joinToString(" ").ifEmpty { NOT_PROVIDED })
                    Detail("GPU 廠商", d.gpuVendor ?: NOT_PROVIDED)
                    Detail("OpenGL ES", d.openGlVersion ?: NOT_PROVIDED)
                    if (s != null) {
                        Detail("目前更新率", hz(s.currentRefreshHz))
                        Detail("支援的更新率", s.supportedRefreshHz.joinToString(" / ") { hz(it) }.ifEmpty { NOT_PROVIDED })
                        Detail("HDR", s.hdrTypes.joinToString(", ").ifEmpty { "不支援或未提供" })
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
