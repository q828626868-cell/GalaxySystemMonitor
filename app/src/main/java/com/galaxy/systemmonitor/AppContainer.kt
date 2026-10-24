package com.galaxy.systemmonitor

import android.content.Context
import com.galaxy.systemmonitor.data.MonitorRepository
import com.galaxy.systemmonitor.data.db.AppDatabase
import com.galaxy.systemmonitor.data.settings.SettingsRepository
import com.galaxy.systemmonitor.data.source.AppUsageReader
import com.galaxy.systemmonitor.data.source.BatteryReader
import com.galaxy.systemmonitor.data.source.DeviceInfoReader
import com.galaxy.systemmonitor.data.source.MemoryReader
import com.galaxy.systemmonitor.data.source.NetworkReader
import com.galaxy.systemmonitor.data.source.PowerReader
import com.galaxy.systemmonitor.data.source.StorageReader
import com.galaxy.systemmonitor.work.NetworkWatcher
import com.galaxy.systemmonitor.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container; one instance per process. */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    val repository: MonitorRepository by lazy {
        MonitorRepository(
            db = database,
            batteryReader = BatteryReader(context),
            memoryReader = MemoryReader(context),
            storageReader = StorageReader(context),
            networkReader = NetworkReader(context),
        )
    }

    val powerReader: PowerReader by lazy { PowerReader(context) }
    val appUsageReader: AppUsageReader by lazy { AppUsageReader(context) }
    val deviceInfoReader: DeviceInfoReader by lazy { DeviceInfoReader(context) }
    private val networkWatcher by lazy { NetworkWatcher(context, repository, appScope) }

    fun start() {
        // Keep the periodic sampling job in sync with the settings.
        appScope.launch {
            settings.settings
                .map { it.backgroundMonitoring to it.sampleIntervalMinutes }
                .distinctUntilChanged()
                .collect { (enabled, interval) -> WorkScheduler.apply(context, enabled, interval) }
        }
        appScope.launch {
            repository.prune(settings.current().retentionDays)
        }
        networkWatcher.start()
    }
}
