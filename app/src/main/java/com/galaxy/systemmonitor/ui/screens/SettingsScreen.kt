package com.galaxy.systemmonitor.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.galaxy.systemmonitor.widget.LargeWidgetReceiver
import com.galaxy.systemmonitor.widget.SmallWidgetReceiver
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.galaxy.systemmonitor.BuildConfigInfo
import com.galaxy.systemmonitor.data.settings.AppSettings
import com.galaxy.systemmonitor.data.settings.ThemeMode
import com.galaxy.systemmonitor.data.settings.WidgetStyle
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.ChoiceChips
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format
import kotlin.math.roundToInt

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class PermissionStates(val phone: Boolean, val batteryStats: Boolean, val usage: Boolean)

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private fun workStateLabel(state: WorkInfo.State?, enabled: Boolean): String = when {
    !enabled -> "已關閉"
    state == null -> "尚未排程"
    state == WorkInfo.State.ENQUEUED -> "已排程，等待系統執行"
    state == WorkInfo.State.RUNNING -> "執行中"
    state == WorkInfo.State.BLOCKED -> "等待條件"
    state == WorkInfo.State.CANCELLED -> "已取消"
    state == WorkInfo.State.FAILED -> "失敗"
    else -> state.name
}

@Composable
fun SettingsScreen(vm: MonitorViewModel, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val workState by vm.workState.collectAsStateWithLifecycle()
    val lastScheduled by vm.lastScheduledSample.collectAsStateWithLifecycle()
    val count by vm.sampleCount.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val status = LocalStatusColors.current

    // Re-check permissions on every resume: they are granted outside the app (ADB / system settings).
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }
    val perms = remember(resumeCount) {
        PermissionStates(
            phone = vm.hasPhonePermission(),
            batteryStats = vm.hasBatteryStatsPermission(),
            usage = vm.hasUsageAccess(),
        )
    }

    // Local slider state, committed when the finger is lifted.
    var warn by remember(settings.tempWarnC) { mutableFloatStateOf(settings.tempWarnC.toFloat()) }
    var high by remember(settings.tempHighC) { mutableFloatStateOf(settings.tempHighC.toFloat()) }

    LaunchedEffect(Unit) { vm.refreshLiveOnly() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除歷史紀錄？") },
            text = {
                Text(
                    "將刪除本 App 自己保存的全部 $count 筆監控紀錄與網路變化紀錄，無法復原。\n\n" +
                        "此操作只影響本 App 的私有資料庫，不會刪除手機中的照片、檔案或其他 App 的任何資料。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                }) { Text("清除", color = status.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::consumeMessage,
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("確定") } },
        )
    }

    ScreenScaffold(title = "設定", onBack = onBack) {
        item {
            SectionCard(title = "外觀") {
                Label("深色模式")
                ChoiceChips(ThemeMode.entries, settings.themeMode, { it.label }, vm::setThemeMode)
                SwitchRow("深色模式使用 AMOLED 純黑背景", settings.amoledBlack, vm::setAmoledBlack)
                Label("Widget 外觀")
                ChoiceChips(WidgetStyle.entries, settings.widgetStyle, { it.label }, vm::setWidgetStyle)
                SwitchRow("大型 Widget 顯示 24 小時電量趨勢", settings.widgetShowTrend, vm::setWidgetShowTrend)
                val widgetManager = remember { AppWidgetManager.getInstance(context) }
                if (widgetManager.isRequestPinAppWidgetSupported) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                widgetManager.requestPinAppWidget(ComponentName(context, SmallWidgetReceiver::class.java), null, null)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("加入小型 Widget") }
                        OutlinedButton(
                            onClick = {
                                widgetManager.requestPinAppWidget(ComponentName(context, LargeWidgetReceiver::class.java), null, null)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("加入大型 Widget") }
                    }
                } else {
                    Text(
                        "此啟動器不支援從 App 直接加入 Widget，請長按桌面空白處，選擇「小工具」後找到 Galaxy System Monitor。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionCard(title = "背景監控") {
                SwitchRow("定期在背景記錄資料", settings.backgroundMonitoring, vm::setBackgroundMonitoring)
                if (settings.backgroundMonitoring) {
                    Label("取樣間隔（系統可能延後執行）")
                    ChoiceChips(AppSettings.INTERVAL_CHOICES, settings.sampleIntervalMinutes, { "$it 分鐘" }, vm::setSampleInterval)
                }
                MetricRow("背景工作狀態", workStateLabel(workState, settings.backgroundMonitoring))
                MetricRow(
                    "最近一次背景取樣",
                    lastScheduled?.let { "${Format.fullDateTime(it)}（${Format.relative(it, now)}）" } ?: "尚未執行",
                )
                InfoNote(
                    "背景取樣使用 Android WorkManager，最短間隔 15 分鐘，實際執行時間由系統決定（省電模式、休眠時可能延後）。" +
                        "本 App 不使用常駐前景服務，也不會持續喚醒手機。若本 App 被列入 Samsung 電池設定中的休眠或深度休眠應用程式，" +
                        "背景取樣可能停止；可在 App 資訊的「電池」項目中將其設為「不受限制」。",
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("開啟本 App 的系統設定") }
            }
        }
        item {
            SectionCard(title = "提示門檻") {
                Label("電池溫度「偏高」：${warn.roundToInt()}°C")
                Slider(
                    value = warn,
                    onValueChange = { warn = it },
                    valueRange = AppSettings.TEMP_WARN_RANGE.first.toFloat()..AppSettings.TEMP_WARN_RANGE.last.toFloat(),
                    steps = AppSettings.TEMP_WARN_RANGE.last - AppSettings.TEMP_WARN_RANGE.first - 1,
                    onValueChangeFinished = {
                        val w = warn.roundToInt()
                        vm.setTemperatureThresholds(w, maxOf(high.roundToInt(), w + 1))
                    },
                )
                Label("電池溫度「高溫警告」：${high.roundToInt()}°C")
                Slider(
                    value = high,
                    onValueChange = { high = it },
                    valueRange = 36f..AppSettings.TEMP_HIGH_MAX.toFloat(),
                    steps = AppSettings.TEMP_HIGH_MAX - 36 - 1,
                    onValueChangeFinished = { vm.setTemperatureThresholds(warn.roundToInt(), high.roundToInt()) },
                )
                Label("剩餘儲存空間低於")
                ChoiceChips(AppSettings.LOW_STORAGE_CHOICES, settings.lowStoragePercent, { "$it%" }, vm::setLowStoragePercent)
                Text(
                    "這些提示只依照上述門檻判斷，不是官方硬體診斷。高溫警告門檻必須高於偏高門檻。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "資料") {
                Label("歷史資料保存期限")
                ChoiceChips(AppSettings.RETENTION_CHOICES, settings.retentionDays, { "$it 天" }, vm::setRetentionDays)
                MetricRow("目前紀錄筆數", "$count")
                Button(
                    onClick = { vm.refresh(manual = true) },
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (refreshing) "重新整理中…" else "立即重新整理並記錄") }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = status.danger),
                ) { Text("清除本 App 的歷史紀錄") }
            }
        }
        item {
            SectionCard(title = "權限") {
                fun state(granted: Boolean) = if (granted) "已授權" else "未授權"
                MetricRow("網路狀態（一般權限）", "安裝時自動授予")
                MetricRow("電話（選用，僅讀取行動網路類型）", state(perms.phone))
                MetricRow("BATTERY_STATS（選用，電池健康度）", state(perms.batteryStats))
                MetricRow("使用情況存取權（選用，App 使用情況）", state(perms.usage))
                Text(
                    "選用權限都可以不給，只會影響對應的單一功能。本 App 不需要 Root、無障礙服務、通知存取權、" +
                        "檔案或媒體存取權限，也沒有網際網路權限。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "關於本應用程式") {
                MetricRow("版本", BuildConfigInfo.versionName(context))
                Text(
                    "Galaxy System Monitor 只透過 Android 公開 API 讀取電池、記憶體、儲存空間與網路狀態，" +
                        "並將歷史紀錄保存在本機的 App 私有資料庫。不需要帳號、不含廣告或分析 SDK、不上傳任何資料，" +
                        "也已停用雲端備份。本 App 只負責監控與顯示，不提供清理、加速或刪除檔案功能。\n\n" +
                        "本 App 與 Samsung 無關，顯示的狀態提示不是官方硬體診斷。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
