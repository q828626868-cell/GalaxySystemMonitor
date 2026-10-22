# 安全與隱私檢查報告：Galaxy System Monitor 1.0.0

檢查日期：2026-10-22。範圍：`app/src/main` 原始碼、合併後的 AndroidManifest、Debug APK（`aapt2 dump permissions`）、release runtime 依賴樹、模擬器實測。
這是開發過程中的自我檢查紀錄，**不是第三方安全稽核**。

## 1. 最終 APK 宣告的權限

| 權限 | 類型 | 來源 | 用途 |
|---|---|---|---|
| `ACCESS_NETWORK_STATE` | 一般（自動授予） | App | 透過 ConnectivityManager 讀取連線狀態、類型、系統驗證結果。 |
| `READ_PHONE_STATE` | 危險（執行時，**選用**） | App | 只在使用者於網路頁按「顯示行動網路類型」時要求；只讀取行動數據網路類型（4G／5G）。拒絕不影響其他功能。 |
| `WAKE_LOCK` | 一般 | WorkManager | WorkManager 執行排程工作期間的短暫 wake lock（只在本 App 的取樣工作執行時）。 |
| `RECEIVE_BOOT_COMPLETED` | 一般 | WorkManager | 重新開機後恢復已排程的週期取樣。 |
| `com.galaxy.systemmonitor.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 簽章（App 自有） | AndroidX Core | 保護 `RECEIVER_NOT_EXPORTED` 動態接收器，只有本 App 能使用。 |

**刻意不包含**：`INTERNET`、`FOREGROUND_SERVICE`（已用 `tools:node="remove"` 移除 WorkManager 帶入的宣告與前景服務元件）、`MANAGE_EXTERNAL_STORAGE`、`READ/WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_*`、`REQUEST_DELETE_PACKAGES`、`PACKAGE_USAGE_STATS`、`POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、位置權限、無障礙服務、通知存取。

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

原始碼中所有刪除操作（`grep` 檢查 `delete`、`File(`、`Runtime.`、`ProcessBuilder`、`killBackgroundProcesses`、`MediaStore`）只有以下四條 SQL，全部作用於 App 自己的資料表：

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
- 資料庫上限由保存期限決定：以 15 分鐘間隔、最長 90 天計算，約 8,640 筆。

## 7. 已知限制與注意事項

- `READ_PHONE_STATE` 在 Android 上屬於「電話」權限群組，系統對話框的措辭會比實際用途廣；本 App 只呼叫 `getDataNetworkType` 與 `TelephonyDisplayInfo`。
- Widget receiver 必須 exported，任何 App 都能觸發重新繪製（無資料外洩，只會多一次本機讀取）。
- 未進行混淆後 Release 版的實機驗證，也未設定 Release 簽章。
- 尚未在實體 Galaxy S25 Ultra 上驗證。
