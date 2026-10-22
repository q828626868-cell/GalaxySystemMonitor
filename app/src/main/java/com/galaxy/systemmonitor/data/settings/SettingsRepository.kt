package com.galaxy.systemmonitor.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) { SYSTEM("跟隨系統"), DARK("深色"), LIGHT("淺色") }

enum class WidgetStyle(val label: String) {
    AMOLED("AMOLED 純黑"),
    GRAPHITE("深灰"),
    FOLLOW_SYSTEM("跟隨系統深淺色"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = true,
    val widgetStyle: WidgetStyle = WidgetStyle.AMOLED,
    val widgetShowTrend: Boolean = true,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val tempWarnC: Int = 40,
    val tempHighC: Int = 45,
    val lowStoragePercent: Int = 10,
    val backgroundMonitoring: Boolean = true,
    val sampleIntervalMinutes: Int = 15,
) {
    /** Clamps values that may come from an older or corrupted preference file. */
    fun sanitized(): AppSettings {
        val warn = tempWarnC.coerceIn(TEMP_WARN_RANGE)
        return copy(
            retentionDays = retentionDays.takeIf { it in RETENTION_CHOICES } ?: DEFAULT_RETENTION_DAYS,
            tempWarnC = warn,
            tempHighC = tempHighC.coerceIn(warn + 1, TEMP_HIGH_MAX),
            lowStoragePercent = lowStoragePercent.takeIf { it in LOW_STORAGE_CHOICES } ?: 10,
            sampleIntervalMinutes = sampleIntervalMinutes.takeIf { it in INTERVAL_CHOICES } ?: 15,
        )
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        val RETENTION_CHOICES = listOf(7, 14, 30, 60, 90)
        val INTERVAL_CHOICES = listOf(15, 30, 60)
        val LOW_STORAGE_CHOICES = listOf(5, 10, 15, 20)
        val TEMP_WARN_RANGE = 35..50
        const val TEMP_HIGH_MAX = 55
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val AMOLED = booleanPreferencesKey("amoled_black")
        val WIDGET_STYLE = stringPreferencesKey("widget_style")
        val WIDGET_TREND = booleanPreferencesKey("widget_trend")
        val RETENTION = intPreferencesKey("retention_days")
        val TEMP_WARN = intPreferencesKey("temp_warn_c")
        val TEMP_HIGH = intPreferencesKey("temp_high_c")
        val LOW_STORAGE = intPreferencesKey("low_storage_percent")
        val BACKGROUND = booleanPreferencesKey("background_monitoring")
        val INTERVAL = intPreferencesKey("sample_interval_minutes")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: d.themeMode,
                amoledBlack = p[Keys.AMOLED] ?: d.amoledBlack,
                widgetStyle = WidgetStyle.entries.firstOrNull { it.name == p[Keys.WIDGET_STYLE] } ?: d.widgetStyle,
                widgetShowTrend = p[Keys.WIDGET_TREND] ?: d.widgetShowTrend,
                retentionDays = p[Keys.RETENTION] ?: d.retentionDays,
                tempWarnC = p[Keys.TEMP_WARN] ?: d.tempWarnC,
                tempHighC = p[Keys.TEMP_HIGH] ?: d.tempHighC,
                lowStoragePercent = p[Keys.LOW_STORAGE] ?: d.lowStoragePercent,
                backgroundMonitoring = p[Keys.BACKGROUND] ?: d.backgroundMonitoring,
                sampleIntervalMinutes = p[Keys.INTERVAL] ?: d.sampleIntervalMinutes,
            ).sanitized()
        }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.THEME] = mode.name }
    suspend fun setAmoledBlack(enabled: Boolean) = update { it[Keys.AMOLED] = enabled }
    suspend fun setWidgetStyle(style: WidgetStyle) = update { it[Keys.WIDGET_STYLE] = style.name }
    suspend fun setWidgetShowTrend(enabled: Boolean) = update { it[Keys.WIDGET_TREND] = enabled }
    suspend fun setRetentionDays(days: Int) = update { it[Keys.RETENTION] = days }
    suspend fun setLowStoragePercent(percent: Int) = update { it[Keys.LOW_STORAGE] = percent }
    suspend fun setBackgroundMonitoring(enabled: Boolean) = update { it[Keys.BACKGROUND] = enabled }
    suspend fun setSampleInterval(minutes: Int) = update { it[Keys.INTERVAL] = minutes }

    suspend fun setTemperatureThresholds(warnC: Int, highC: Int) = update {
        val fixed = AppSettings(tempWarnC = warnC, tempHighC = highC).sanitized()
        it[Keys.TEMP_WARN] = fixed.tempWarnC
        it[Keys.TEMP_HIGH] = fixed.tempHighC
    }
}
