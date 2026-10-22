package com.galaxy.systemmonitor.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxy.systemmonitor.data.db.NetworkDetection
import com.galaxy.systemmonitor.data.model.CellularStatus
import com.galaxy.systemmonitor.data.model.NetworkTransport
import com.galaxy.systemmonitor.data.model.unavailableReason
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.components.BigValue
import com.galaxy.systemmonitor.ui.components.InfoNote
import com.galaxy.systemmonitor.ui.components.MetricRow
import com.galaxy.systemmonitor.ui.components.ScreenScaffold
import com.galaxy.systemmonitor.ui.components.SectionCard
import com.galaxy.systemmonitor.ui.components.UnavailableText
import com.galaxy.systemmonitor.ui.theme.LocalStatusColors
import com.galaxy.systemmonitor.util.Format

private fun yesNoUnknown(value: Boolean?): String = when (value) {
    true -> "是"
    false -> "否"
    null -> "未知"
}

@Composable
fun NetworkScreen(vm: MonitorViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val lastEvent by vm.latestNetworkEvent.collectAsStateWithLifecycle()
    val events by vm.recentNetworkEvents.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshLiveOnly()
    }

    ScreenScaffold(title = "網路狀態", onBack = onBack) {
        item {
            val reading = live?.network
            val n = reading?.valueOrNull()
            SectionCard(title = "目前連線") {
                if (n == null) {
                    UnavailableText(reading?.unavailableReason() ?: "讀取中…")
                } else {
                    BigValue(if (n.connected) n.transport.label else "無連線")
                    MetricRow("已連線至網路", if (n.connected) "是" else "否")
                    MetricRow("連線類型", n.transport.label)
                    if (n.connected) {
                        MetricRow("經由 VPN", if (n.viaVpn) "是" else "否")
                        MetricRow("網路提供網際網路能力", yesNoUnknown(n.hasInternetCapability))
                        MetricRow(
                            "系統判定可連上網際網路",
                            yesNoUnknown(n.validated),
                            if (n.validated == true) status.good else status.warn,
                        )
                        MetricRow("計量制連線", yesNoUnknown(n.metered))
                    }
                    if (n.transport == NetworkTransport.CELLULAR) {
                        MetricRow("行動網路類型", n.cellular.displayText)
                    }
                    live?.let { MetricRow("更新時間", Format.fullDateTime(it.timestamp)) }
                }
            }
        }
        val n = live?.network?.valueOrNull()
        if (n?.transport == NetworkTransport.CELLULAR && n.cellular.status == CellularStatus.PERMISSION_REQUIRED) {
            item {
                SectionCard(title = "行動網路類型（選用）") {
                    Text(
                        "Android 需要「電話」權限才能讀取目前的行動數據網路類型（例如 4G、5G）。" +
                            "本 App 只用它讀取網路類型，不會讀取電話號碼、通話或簡訊。不授權也不影響其他功能。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("顯示行動網路類型") }
                }
            }
        }
        item {
            SectionCard(title = "最後一次網路狀態變化") {
                val e = lastEvent
                if (e == null) {
                    Text("尚無紀錄", style = MaterialTheme.typography.bodyMedium)
                } else {
                    MetricRow("時間", Format.fullDateTime(e.timestamp))
                    MetricRow(
                        "變為",
                        if (e.connected) NetworkTransport.fromName(e.transport).label else "無連線",
                    )
                    MetricRow(
                        "偵測方式",
                        NetworkDetection.entries.firstOrNull { it.name == e.detectedBy }?.label ?: "未知",
                    )
                }
            }
        }
        if (events.size > 1) {
            item {
                SectionCard(title = "近期變化紀錄") {
                    events.forEach { e ->
                        val text = buildString {
                            append(if (e.connected) NetworkTransport.fromName(e.transport).label else "無連線")
                            if (e.connected) {
                                append(
                                    when (e.validated) {
                                        true -> " · 已驗證"
                                        false -> " · 未驗證"
                                        null -> ""
                                    },
                                )
                            }
                        }
                        MetricRow(Format.fullDateTime(e.timestamp), text)
                    }
                }
            }
        }
        item {
            InfoNote(
                "「已連線」只表示裝置有預設網路；「系統判定可連上網際網路」是 Android 自身的驗證結果（NET_CAPABILITY_VALIDATED）；" +
                    "兩者都不是實際測得的速度或延遲。本 App 不會主動進行網速或延遲測試，也沒有網際網路權限，不會產生任何流量。" +
                    "App 未執行時發生的網路變化，會在下一次取樣時才被偵測到，因此實際變化時間可能更早。",
            )
        }
    }
}
