package com.galaxy.systemmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import com.galaxy.systemmonitor.GalaxyMonitorApp
import com.galaxy.systemmonitor.data.StoreResult
import com.galaxy.systemmonitor.data.db.NetworkEventEntity
import com.galaxy.systemmonitor.data.db.SampleEntity
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.data.db.TimedValue
import com.galaxy.systemmonitor.data.model.BatteryInfo
import com.galaxy.systemmonitor.data.model.SystemSnapshot
import com.galaxy.systemmonitor.data.settings.AppSettings
import com.galaxy.systemmonitor.data.settings.SettingsRepository
import com.galaxy.systemmonitor.data.settings.ThemeMode
import com.galaxy.systemmonitor.data.settings.WidgetStyle
import com.galaxy.systemmonitor.domain.DrainSummary
import com.galaxy.systemmonitor.widget.WidgetUpdater
import com.galaxy.systemmonitor.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(private val app: GalaxyMonitorApp) : ViewModel() {
    private val container = app.container
    private val repo = container.repository
    private val settingsRepo: SettingsRepository = container.settings

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val live: StateFlow<SystemSnapshot?> = repo.live
    val databaseError: StateFlow<String?> = repo.databaseError

    private val _now = MutableStateFlow(System.currentTimeMillis())
    /** Current time, refreshed on every capture so relative times and windows stay correct. */
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Up to 7 days (+1 h margin) of samples; null while loading. */
    val history: StateFlow<List<SampleEntity>?> =
        repo.observeSince(System.currentTimeMillis() - 7 * DAY - HOUR).state(null)

    val drain: StateFlow<DrainSummary?> = combine(history, _now) { samples, now ->
        samples?.let { DrainSummary.from(it, now) }
    }.flowOn(Dispatchers.Default).state(null)

    val maxTemperature: StateFlow<TimedValue?> = repo.observeMaxTemperature().state(null)
    val minTemperature: StateFlow<TimedValue?> = repo.observeMinTemperature().state(null)
    val latestNetworkEvent: StateFlow<NetworkEventEntity?> = repo.observeLatestNetworkEvent().state(null)
    val recentNetworkEvents: StateFlow<List<NetworkEventEntity>> = repo.observeRecentNetworkEvents(30).state(emptyList())
    val recentSamples: StateFlow<List<SampleEntity>> = repo.observeRecent(100).state(emptyList())
    val sampleCount: StateFlow<Int> = repo.observeCount().state(0)
    val oldestSample: StateFlow<Long?> = repo.observeOldestTimestamp().state(null)
    val lastScheduledSample: StateFlow<Long?> = repo.observeLastScheduled().state(null)
    val workState: StateFlow<WorkInfo.State?> = WorkScheduler.observeState(app).state(null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** Takes a reading and stores it. Automatic calls skip storing an unchanged state within a minute. */
    fun refresh(manual: Boolean) {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val outcome = repo.captureAndStore(
                    if (manual) SampleSource.MANUAL else SampleSource.APP_OPEN,
                    minSpacingMillis = if (manual) 0 else 60_000,
                )
                if (outcome.store == StoreResult.STORED) WidgetUpdater.updateAll(app)
            } finally {
                _now.value = System.currentTimeMillis()
                _refreshing.value = false
            }
        }
    }

    /** Re-reads live values for the visible screen without writing to the database. */
    fun refreshLiveOnly() {
        viewModelScope.launch {
            repo.capture()
            _now.value = System.currentTimeMillis()
        }
    }

    fun batteryChanges(): Flow<BatteryInfo> = repo.batteryChanges()

    fun onBatteryChanged(info: BatteryInfo) {
        viewModelScope.launch {
            if (repo.onBatteryBroadcast(info)) WidgetUpdater.updateAll(app)
            _now.value = System.currentTimeMillis()
        }
    }

    fun hasPhonePermission(): Boolean = repo.hasPhonePermission()

    // ---- Settings ----

    private fun updateSettings(refreshWidgets: Boolean = false, block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            settingsRepo.block()
            if (refreshWidgets) WidgetUpdater.updateAll(app)
        }
    }

    fun setThemeMode(mode: ThemeMode) = updateSettings { setThemeMode(mode) }
    fun setAmoledBlack(enabled: Boolean) = updateSettings { setAmoledBlack(enabled) }
    fun setWidgetStyle(style: WidgetStyle) = updateSettings(refreshWidgets = true) { setWidgetStyle(style) }
    fun setWidgetShowTrend(enabled: Boolean) = updateSettings(refreshWidgets = true) { setWidgetShowTrend(enabled) }
    fun setBackgroundMonitoring(enabled: Boolean) = updateSettings { setBackgroundMonitoring(enabled) }
    fun setSampleInterval(minutes: Int) = updateSettings { setSampleInterval(minutes) }
    fun setLowStoragePercent(percent: Int) = updateSettings(refreshWidgets = true) { setLowStoragePercent(percent) }
    fun setTemperatureThresholds(warn: Int, high: Int) =
        updateSettings(refreshWidgets = true) { setTemperatureThresholds(warn, high) }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepo.setRetentionDays(days)
            val removed = repo.prune(days)
            if (removed > 0) _message.value = "已依新的保存期限移除 $removed 筆過期紀錄"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearHistory()
                .onSuccess {
                    _message.value = "已清除本 App 的 $it 筆監控紀錄"
                    WidgetUpdater.updateAll(app)
                }
                .onFailure { _message.value = "清除失敗：${it.message ?: it.javaClass.simpleName}" }
        }
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GalaxyMonitorApp
                MonitorViewModel(app)
            }
        }
    }
}
