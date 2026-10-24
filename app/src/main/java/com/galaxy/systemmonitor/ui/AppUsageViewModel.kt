package com.galaxy.systemmonitor.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.galaxy.systemmonitor.GalaxyMonitorApp
import com.galaxy.systemmonitor.data.model.AppScreenTime
import com.galaxy.systemmonitor.data.model.AppTraffic
import com.galaxy.systemmonitor.data.model.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class UsageRange(val label: String) {
    TODAY("今天"),
    H24("最近 24 小時"),
    D7("最近 7 天"),
    ;

    fun start(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        TODAY -> LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        H24 -> now - 24 * 3_600_000L
        D7 -> now - 7 * 24 * 3_600_000L
    }
}

data class AppUsageState(
    /** null until the first check. */
    val hasAccess: Boolean? = null,
    val range: UsageRange = UsageRange.TODAY,
    val start: Long = 0,
    val end: Long = 0,
    val screenTime: Reading<List<AppScreenTime>>? = null,
    val traffic: Reading<List<AppTraffic>>? = null,
    val loading: Boolean = false,
)

class AppUsageViewModel(app: GalaxyMonitorApp) : ViewModel() {
    private val reader = app.container.appUsageReader

    private val _state = MutableStateFlow(AppUsageState())
    val state: StateFlow<AppUsageState> = _state.asStateFlow()
    private var job: Job? = null

    fun setRange(range: UsageRange) {
        if (range == _state.value.range) return
        _state.value = _state.value.copy(range = range)
        reload()
    }

    /** Re-checks access (it may have been granted in Settings meanwhile) and reloads both lists. */
    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            val range = _state.value.range
            val access = withContext(Dispatchers.IO) { reader.hasUsageAccess() }
            if (!access) {
                _state.value = _state.value.copy(hasAccess = false, screenTime = null, traffic = null, loading = false)
                return@launch
            }
            _state.value = _state.value.copy(hasAccess = true, loading = true)
            val end = System.currentTimeMillis()
            val start = range.start(end)
            val screen = async(Dispatchers.IO) { reader.screenTime(start, end) }
            val traffic = async(Dispatchers.IO) { reader.traffic(start, end) }
            _state.value = _state.value.copy(
                start = start,
                end = end,
                screenTime = screen.await(),
                traffic = traffic.await(),
                loading = false,
            )
        }
    }

    suspend fun icon(packageName: String): Bitmap? = withContext(Dispatchers.IO) { reader.icon(packageName) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GalaxyMonitorApp
                AppUsageViewModel(app)
            }
        }
    }
}
