package com.galaxy.systemmonitor.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galaxy.systemmonitor.ui.components.DataSource
import com.galaxy.systemmonitor.ui.components.Caption
import com.galaxy.systemmonitor.ui.components.HeroCard
import com.galaxy.systemmonitor.ui.components.HeroLabel
import com.galaxy.systemmonitor.ui.components.HeroPill
import com.galaxy.systemmonitor.ui.components.HeroPills
import com.galaxy.systemmonitor.ui.components.HeroSubtitle
import com.galaxy.systemmonitor.ui.components.HeroValue
import com.galaxy.systemmonitor.ui.components.RingGauge
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
        val reading = live?.network
        val current = reading?.valueOrNull()
        if (current == null) {
            item { SectionCard { UnavailableText(reading?.unavailableReason() ?: "讀取中…") } }
        } else {
            item {
                val ok = current.connected && current.validated == true
                val stateColor = when {
                    !current.connected -> status.danger
                    ok -> status.good
                    else -> status.warn
                }
                HeroCard(
                    trailing = {
                        RingGauge(
                            if (current.connected) 1f else 0f,
                            stateColor,
                            centerText = when {
                                !current.connected -> "離線"
                                ok -> "可上網"
                                else -> "未驗證"
                            },
                        )
                    },
                ) {
                    HeroLabel("目前網路", DataSource.MEASURED)
                    HeroValue(if (current.connected) current.transport.label else "無連線", large = false)
                    if (current.transport == NetworkTransport.CELLULAR) HeroSubtitle(current.cellular.displayText)
                    live?.let { HeroSubtitle("更新於 ${Format.dateTime(it.timestamp)}") }
                    if (current.connected) {
                        HeroPills {
                            if (current.viaVpn) HeroPill("VPN")
                            if (current.metered == true) HeroPill("計量制")
                            if (current.hasInternetCapability == true) HeroPill("網際網路能力")
                        }
                    }
                }
            }
            if (current.connected) {
                item {
                    SectionCard(title = "連線細節", source = DataSource.MEASURED) {
                        MetricRow("經由 VPN", if (current.viaVpn) "是" else "否")
                        MetricRow("網路提供網際網路能力", yesNoUnknown(current.hasInternetCapability))
                        MetricRow(
                            "系統判定可連上網際網路",
                            yesNoUnknown(current.validated),
                            if (current.validated == true) status.good else status.warn,
                        )
                        MetricRow("計量制連線", yesNoUnknown(current.metered))
                    }
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
            SectionCard(title = "網路變化紀錄", source = DataSource.MEASURED) {
                val list = events.ifEmpty { listOfNotNull(lastEvent) }
                if (list.isEmpty()) {
                    UnavailableText("尚無紀錄")
                } else {
                    list.forEachIndexed { i, e ->
                        val color = when {
                            !e.connected -> status.danger
                            e.validated == false -> status.warn
                            else -> status.good
                        }
                        TimelineRow(
                            color = color,
                            title = buildString {
                                append(if (e.connected) NetworkTransport.fromName(e.transport).label else "無連線")
                                if (e.connected && e.validated == true) append(" · 已驗證")
                                if (e.connected && e.validated == false) append(" · 未驗證")
                            },
                            subtitle = Format.fullDateTime(e.timestamp) + " · " +
                                (NetworkDetection.entries.firstOrNull { it.name == e.detectedBy }?.label ?: "未知"),
                            last = i == list.lastIndex,
                        )
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

/** One entry of a vertical timeline: a colored dot with a connecting line below it. */
@Composable
private fun TimelineRow(color: Color, title: String, subtitle: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            if (!last) {
                Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Caption(subtitle)
        }
    }
}
