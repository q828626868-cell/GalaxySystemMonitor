package com.galaxy.systemmonitor.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.ui.AppUsageViewModel
import com.galaxy.systemmonitor.ui.UsageRange
import com.galaxy.systemmonitor.ui.components.ChoiceChips
import com.galaxy.systemmonitor.ui.components.HeroCard
import com.galaxy.systemmonitor.ui.components.HeroLabel
import com.galaxy.systemmonitor.ui.components.HeroPill
import com.galaxy.systemmonitor.ui.components.HeroPills
import com.galaxy.systemmonitor.ui.components.HeroValue
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.UnavailableText
import com.galaxy.systemmonitor.ui.components.UsageBar
import com.galaxy.systemmonitor.util.Format

private enum class UsageTab(val label: String) { SCREEN("使用時間"), TRAFFIC("網路流量") }

private const val MAX_ROWS = 50

@Composable
fun AppUsageScreen(onBack: () -> Unit, vm: AppUsageViewModel = viewModel(factory = AppUsageViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(UsageTab.SCREEN) }

    // Returning from the usage-access settings page re-checks the permission.
    LifecycleResumeEffect(Unit) {
        vm.reload()
        onPauseOrDispose { }
    }

    ScreenScaffold(title = "App 使用情況", onBack = onBack) {
        if (state.hasAccess == false) {
            item { UsageAccessCard() }
            return@ScreenScaffold
        }
        item { ChoiceChips(UsageTab.entries, tab, { it.label }) { tab = it } }
        item { ChoiceChips(UsageRange.entries, state.range, { it.label }, vm::setRange) }
        if (state.start > 0) {
            item {
                Text(
                    "${Format.fullDateTime(state.start)} – ${Format.fullDateTime(state.end)}" +
                        if (state.loading) "　更新中…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
            UsageTab.SCREEN -> {
                val reading = state.screenTime
                if (reading !is Reading.Ok) {
                    item { SectionCard { UnavailableText((reading as? Reading.Unavailable)?.reason ?: "讀取中…") } }
                } else {
                    val list = reading.value
                    item {
                        HeroCard {
                            HeroLabel("App 前景使用時間 · ${state.range.label}")
                            HeroValue(Format.duration(list.sumOf { it.millis }), large = false)
                            HeroPills {
                                HeroPill("${list.size} 個 App")
                                list.firstOrNull()?.let { HeroPill("最常用：${it.label}") }
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item { SectionCard { UnavailableText("此區間沒有使用紀錄") } }
                    } else {
                        item {
                            SectionCard(title = "依使用時間排序") {
                                val max = list.first().millis.coerceAtLeast(1)
                                list.take(MAX_ROWS).forEach { app ->
                                    AppRow(vm, app.packageName, app.label, if (app.millis < 60_000) "不到 1 分" else Format.duration(app.millis), null, app.millis.toFloat() / max)
                                }
                            }
                        }
                    }
                    item {
                        InfoNote(
                            "依系統的 App 開啟／離開事件計算，螢幕關閉時結束計時；分割畫面時兩個 App 會同時計時。" +
                                "數值可能和「數位健康」略有不同，因為數位健康會排除桌面等系統畫面。",
                        )
                    }
                }
            }
            UsageTab.TRAFFIC -> {
                val reading = state.traffic
                if (reading !is Reading.Ok) {
                    item { SectionCard { UnavailableText((reading as? Reading.Unavailable)?.reason ?: "讀取中…") } }
                } else {
                    val list = reading.value
                    item {
                        HeroCard {
                            HeroLabel("網路流量 · ${state.range.label}")
                            HeroValue(Format.bytes(list.sumOf { it.totalBytes }), large = false)
                            HeroPills {
                                HeroPill("Wi-Fi ${Format.bytes(list.sumOf { it.wifiBytes })}")
                                HeroPill("行動 ${Format.bytes(list.sumOf { it.mobileBytes })}")
                                HeroPill("背景 ${Format.bytes(list.sumOf { it.backgroundBytes })}")
                            }
                        }
                    }
                    if (list.isEmpty()) {
                        item { SectionCard { UnavailableText("此區間沒有流量紀錄") } }
                    } else {
                        item {
                            SectionCard(title = "依流量排序") {
                                val max = list.first().totalBytes.coerceAtLeast(1)
                                list.take(MAX_ROWS).forEach { t ->
                                    AppRow(
                                        vm, t.packageName, t.label, Format.bytes(t.totalBytes),
                                        "Wi-Fi ${Format.bytes(t.wifiBytes)} · 行動 ${Format.bytes(t.mobileBytes)} · 背景 ${Format.bytes(t.backgroundBytes)}",
                                        t.totalBytes.toFloat() / max,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        InfoNote(
                            "流量為上傳＋下載。系統以約 2 小時為一個區塊記錄流量，所以起訖時間附近的流量可能多算或少算一些。" +
                                "「背景」指 App 不在前景時產生的流量。共用同一個 UID 的多個系統套件無法再細分。",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(vm: AppUsageViewModel, packageName: String?, label: String, amount: String, detail: String?, fraction: Float) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val icon by produceState<ImageBitmap?>(null, packageName) {
            value = packageName?.let { vm.icon(it)?.asImageBitmap() }
        }
        val i = icon
        if (i != null) {
            Image(i, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            UsageBar(fraction)
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UsageAccessCard() {
    val context = LocalContext.current
    SectionCard(title = "需要「使用情況存取權」") {
        Text(
            "此功能需要系統的「使用情況存取權」，才能讀取各 App 的使用時間與網路流量。" +
                "授權後本 App 能看到你使用了哪些 App 及使用多久；資料只在手機上顯示，本 App 沒有網路權限，無法傳出。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("開啟系統設定授權") }
        Text(
            "在清單中找到「Galaxy System Monitor」並開啟。也可以用電腦執行：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                "adb shell appops set com.galaxy.systemmonitor GET_USAGE_STATS allow",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
