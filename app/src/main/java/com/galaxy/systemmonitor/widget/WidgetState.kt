package com.galaxy.systemmonitor.widget

import android.content.Context
import com.galaxy.systemmonitor.appContainer
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.MemoryInfo
import com.galaxy.systemmonitor.data.model.NetworkInfo
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.data.model.StorageInfo
import com.galaxy.systemmonitor.data.model.valueOrNull
import com.galaxy.systemmonitor.data.settings.AppSettings
import com.galaxy.systemmonitor.domain.ChartPoint
import com.galaxy.systemmonitor.domain.DrainSummary
import com.galaxy.systemmonitor.domain.Series
import com.galaxy.systemmonitor.domain.TempLevel
import com.galaxy.systemmonitor.domain.TemperatureAnalyzer
import kotlinx.coroutines.CancellationException

/** Everything a widget needs to render, loaded once per widget update. */
data class WidgetState(
    val updatedAt: Long?,
    val battery: BatteryInfo?,
    val batteryError: String?,
    val memory: MemoryInfo?,
    val storage: StorageInfo?,
    val network: NetworkInfo?,
    val drain: DrainSummary?,
    val tempLevel: TempLevel,
    val lowStorage: Boolean,
    val trend: List<ChartPoint>,
    val trendStart: Long,
    val trendEnd: Long,
    val settings: AppSettings,
    val historyError: String?,
    val fatalError: String?,
)

object WidgetStateLoader {
    private const val FRESH_MILLIS = 10 * 60_000L
    private const val TREND_WINDOW = 24 * 3_600_000L

    suspend fun load(context: Context): WidgetState {
        val now = System.currentTimeMillis()
        return try {
            val container = context.appContainer
            val settings = container.settings.current()
            val repo = container.repository

            // Reuse a recent in-memory snapshot; otherwise take one cheap local reading.
            val live = repo.live.value
            val snapshot = if (live != null && now - live.timestamp in 0..FRESH_MILLIS) {
                live
            } else {
                repo.captureAndStore(SampleSource.WIDGET).snapshot
            }

            var historyError: String? = null
            val samples = try {
                repo.samplesSince(now - TREND_WINDOW)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                historyError = "歷史紀錄暫時無法讀取"
                emptyList()
            }

            val battery = snapshot.battery.valueOrNull()
            val storage = snapshot.storage.valueOrNull()
            WidgetState(
                updatedAt = snapshot.timestamp,
                battery = battery,
                batteryError = (snapshot.battery as? Reading.Unavailable)?.reason,
                memory = snapshot.memory.valueOrNull(),
                storage = storage,
                network = snapshot.network.valueOrNull(),
                drain = if (historyError == null) DrainSummary.from(samples, now) else null,
                tempLevel = TemperatureAnalyzer.classify(battery?.temperatureC, settings.tempWarnC, settings.tempHighC),
                lowStorage = storage != null && storage.freeFraction * 100 < settings.lowStoragePercent,
                trend = Series.levelChart(samples),
                trendStart = now - TREND_WINDOW,
                trendEnd = now,
                settings = settings,
                historyError = historyError,
                fatalError = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WidgetState(
                updatedAt = null, battery = null, batteryError = null, memory = null, storage = null,
                network = null, drain = null, tempLevel = TempLevel.UNAVAILABLE, lowStorage = false,
                trend = emptyList(), trendStart = now - TREND_WINDOW, trendEnd = now,
                settings = AppSettings(), historyError = null,
                fatalError = "暫時無法讀取資料，請點擊重新整理",
            )
        }
    }
}
