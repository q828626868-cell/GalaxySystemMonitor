# 安全與隱私檢查報告：Galaxy System Monitor 1.1.0

檢查日期：2026-10-24（1.0.0 版於 2026-10-22 檢查）。範圍：`app/src/main` 原始碼、合併後的 AndroidManifest、Debug APK（`aapt2 dump permissions`）、release runtime 依賴樹、Galaxy S25 Ultra 與 Galaxy M14 5G 實機測試。
這是開發過程中的自我檢查紀錄，**不是第三方安全稽核**。

**1.1.0 的主要變更**：新增三個選用權限（`BATTERY_STATS`、`PACKAGE_USAGE_STATS`、`QUERY_ALL_PACKAGES`），以及讀取其他 App 使用統計與 `/sys` CPU 頻率的功能。仍然沒有 `INTERNET` 權限。開發過程中曾試做以 `DUMP` 權限執行 `dumpsys battery` 讀取 Samsung 內部電池欄位，但實測一般 App 會被 SELinux 擋下（`Can't find service: battery`），因此已整個移除，最終 APK 不宣告 `DUMP`，也不執行任何外部指令。

## 1. 最終 APK 宣告的權限

| 權限 | 類型 | 來源 | 用途 |
|---|---|---|---|
| `ACCESS_NETWORK_STATE` | 一般（自動授予） | App | 透過 ConnectivityManager 讀取連線狀態、類型、系統驗證結果。 |
| `READ_PHONE_STATE` | 危險（執行時，**選用**） | App | 只在使用者於網路頁按「顯示行動網路類型」時要求；只讀取行動數據網路類型（4G／5G）。拒絕不影響其他功能。 |
| `WAKE_LOCK` | 一般 | WorkManager | WorkManager 執行排程工作期間的短暫 wake lock（只在本 App 的取樣工作執行時）。 |
| `RECEIVE_BOOT_COMPLETED` | 一般 | WorkManager | 重新開機後恢復已排程的週期取樣。 |
| `com.galaxy.systemmonitor.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 簽章（App 自有） | AndroidX Core | 保護 `RECEIVER_NOT_EXPORTED` 動態接收器，只有本 App 能使用。 |
| `BATTERY_STATS` | 開發（**選用**，只能由使用者以 `adb shell pm grant` 授予） | App | 只用於 `BatteryManager.getIntProperty(BATTERY_PROPERTY_STATE_OF_HEALTH)`。未授權時 App 不呼叫該屬性，畫面顯示「需要以 ADB 授予」。 |
| `PACKAGE_USAGE_STATS` | App-op（**選用**，系統設定「使用情況存取權」或 `adb shell appops`） | App | 只在「App 使用情況」頁使用 `UsageStatsManager.queryEvents` 與 `NetworkStatsManager.querySummary`。未授權時只顯示說明與設定按鈕。 |
| `QUERY_ALL_PACKAGES` | 一般（安裝時授予） | App | 只用於把使用統計中的套件名稱／UID 轉成 App 名稱與圖示（`getApplicationInfo`、`getApplicationIcon`、`getPackagesForUid`）。 |

**刻意不包含**：`INTERNET`、`DUMP`、`FOREGROUND_SERVICE`（已用 `tools:node="remove"` 移除 WorkManager 帶入的宣告與前景服務元件）、`MANAGE_EXTERNAL_STORAGE`、`READ/WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_*`、`REQUEST_DELETE_PACKAGES`、`POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、位置權限、無障礙服務、通知存取。

### 1.1 選用權限可取得的資料範圍

| 權限 | 可讀到的資料 | 本 App 實際讀取 | 是否保存 |
|---|---|---|---|
| `BATTERY_STATS` | 電池統計與受保護的電池屬性 | 只讀健康度百分比一個整數 | 否（只在畫面顯示） |
| `PACKAGE_USAGE_STATS` | 各 App 的前景／背景事件、使用時間、網路流量 | App 開啟／離開與螢幕關閉事件、依 UID 彙總的 Wi-Fi／行動數據位元組數 | 否（只在畫面顯示，圖示快取於記憶體） |
| `QUERY_ALL_PACKAGES` | 已安裝 App 清單 | 只查詢統計中出現的套件名稱與圖示 | 否 |

這些資料都不寫入資料庫或檔案，也因為沒有 `INTERNET` 權限而無法傳出手機。

## 2. 對外公開（exported）元件

| 元件 | 保護 | 說明 |
|---|---|---|
| `MainActivity` | 啟動器入口 | 外部傳入的頁面參數只接受白名單路由（`Destinations.fromIntent`，有儀器測試）。 |
| `SmallWidgetReceiver`／`LargeWidgetReceiver` | 無（AppWidget 標準作法） | 外部最多只能觸發 Widget 重新繪製。 |
| WorkManager `SystemJobService` | `BIND_JOB_SERVICE` | 只有系統能綁定。 |
| Glance／Core RemoteViews 服務 | `BIND_REMOTEVIEWS` | 只有系統能綁定。 |
| WorkManager `DiagnosticsReceiver`、ProfileInstaller | `DUMP` | 只有系統／adb 可呼叫。 |
| `androidx.compose.ui.tooling.PreviewActivity` | 無 | 只存在 Debug 版（`debugImplementation`），Release 版不包含。 |

## 3. 資料儲存位置

| 資料 | 位置 |
|---|---|
| 監控歷史（samples、network_events） | `/data/data/com.galaxy.systemmonitor/databases/monitor.db`（App 私有） |
| 設定 | `/data/data/com.galaxy.systemmonitor/files/datastore/settings.preferences_pb`（App 私有） |
| WorkManager／Glance 內部狀態 | App 私有 `no_backup`、`files/datastore` |

- 不使用外部儲存、MediaStore 或任何寫死的外部路徑。
- `android:allowBackup="false"`，`data_extraction_rules.xml` 同時排除雲端備份與裝置間傳輸，資料不會離開手機。
- 實測：累積約 20 筆紀錄後，整個 App 私有目錄約 1 MB。

## 4. 網路行為

- APK 沒有 `INTERNET` 權限，系統層級就無法建立任何網路連線。
- 原始碼搜尋 `URL(`、`HttpURLConnection`、`OkHttp`、`Socket`、`WebView`：無結果。
- 網路狀態完全來自 `ConnectivityManager` 與系統回呼，不做網速、延遲或連線測試。
- 依賴樹只有 AndroidX、Kotlin／kotlinx、Guava `listenablefuture`（WorkManager）、Okio（DataStore 檔案 I/O）、JSpecify 註解；沒有廣告、分析、追蹤或遙測 SDK。

## 5. 資料清理與刪除

原始碼中所有刪除操作（`grep` 檢查 `delete`、`File(`、`Runtime.`、`ProcessBuilder`、`killBackgroundProcesses`、`MediaStore`）只有以下四條 SQL，全部作用於 App 自己的資料表。1.1.0 新增的 `File(` 只用於**唯讀**的 `/sys/devices/system/cpu/` cpufreq 檔案（讀取失敗時顯示「不可用」）；`Runtime.` 只有 `availableProcessors()`；沒有 `ProcessBuilder` 或外部指令。

| 程式碼 | 觸發時機 |
|---|---|
| `DELETE FROM samples WHERE timestamp < :cutoff` | 每次背景取樣、App 啟動、變更保存期限時（預設 30 天） |
| `DELETE FROM network_events WHERE timestamp < :cutoff` | 同上 |
| `DELETE FROM samples` | 使用者在設定中確認「清除本 App 的歷史紀錄」 |
| `DELETE FROM network_events` | 同上（同一個交易） |

確認對話框明確說明：只刪除本 App 的監控紀錄，不影響照片、檔案或其他 App。沒有清理手機、刪除檔案、關閉其他 App 或修改系統設定的功能。「開啟本 App 的系統設定」按鈕只開啟系統的 App 資訊頁，不做任何變更。

## 6. 背景執行

- 唯一的背景工作：WorkManager 週期工作 `periodic_sampling`（最短 15 分鐘，可在設定中關閉），每次只讀取一次、寫入一筆、清理過期資料後結束，沒有迴圈。
- 不使用前景服務、鬧鐘（AlarmManager）或 wake lock 輪詢。
- `NetworkWatcher` 只登記系統預設網路回呼（不輪詢），寫入前先去抖動，並且只在狀態真正改變時寫入。
- 即時電池廣播與每 30 秒畫面刷新，只在 App 畫面可見時執行（`repeatOnLifecycle(STARTED)`）；寫入有節流（狀態未變且 1 分鐘內不重複寫入）。
- 1.1.0 新增的輪詢（即時電流每 2 秒、CPU 頻率與散熱狀態每 1.5 秒）都是 `StateFlow` 搭配 `WhileSubscribed`，只在對應畫面開著時執行，離開 5 秒後停止，結果不寫入資料庫。
- 資料庫上限由保存期限決定：以 15 分鐘間隔、最長 90 天計算，約 8,640 筆。

## 7. 已知限制與注意事項

- `READ_PHONE_STATE` 在 Android 上屬於「電話」權限群組，系統對話框的措辭會比實際用途廣；本 App 只呼叫 `getDataNetworkType` 與 `TelephonyDisplayInfo`。
- Widget receiver 必須 exported，任何 App 都能觸發重新繪製（無資料外洩，只會多一次本機讀取）。
- 未進行混淆後 Release 版的實機驗證，也未設定 Release 簽章。
- `QUERY_ALL_PACKAGES` 是安裝時自動授予的權限，即使使用者從未開啟「App 使用情況」，App 也有能力列出已安裝的 App；程式只在該頁面、且已有使用情況存取權時才查詢。若要上架 Google Play，此權限需要另外申報理由。
- 以 ADB 授予的權限（`BATTERY_STATS`、使用情況存取權）不會在 App 內提示到期；使用者需要自行收回。設定頁會列出目前的授權狀態。
- 1.1.0 已在 Galaxy S25 Ultra（Android 16）與 Galaxy M14 5G（Android 15）實機測試主要功能，7 個儀器測試在 Galaxy M14 5G 實機上全部通過。
