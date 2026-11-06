package org.isayme.callstats.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStore delegates are process-wide singletons, so multiple repository instances are safe.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = readablePreferences()
        .map { preferences ->
            AppSettings(
                repeatCallWindowMinutes = AppSettings.clampRepeatCallWindow(
                    preferences[KEY_REPEAT_CALL_WINDOW] ?: AppSettings.DEFAULT_REPEAT_CALL_WINDOW_MINUTES,
                ),
                statsPeriodMonths = AppSettings.clampStatsPeriod(
                    preferences[KEY_STATS_PERIOD] ?: AppSettings.DEFAULT_STATS_PERIOD_MONTHS,
                ),
                normalizeNumbers = preferences[KEY_NORMALIZE_NUMBERS]
                    ?: AppSettings.DEFAULT_NORMALIZE_NUMBERS,
                showOverlayOnOutgoing = preferences[KEY_SHOW_OUTGOING_OVERLAY]
                    ?: AppSettings.DEFAULT_SHOW_OVERLAY_ON_OUTGOING,
            )
        }

    val monitorEnabled: Flow<Boolean> = readablePreferences()
        .map { preferences -> preferences[KEY_MONITOR_ENABLED] ?: false }

    val overlayPosition: Flow<OverlayPosition?> = readablePreferences()
        .map { preferences ->
            val x = preferences[KEY_OVERLAY_X]
            val y = preferences[KEY_OVERLAY_Y]
            if (x != null && y != null) OverlayPosition(x, y) else null
        }

    suspend fun saveOverlayPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_OVERLAY_X] = x
            preferences[KEY_OVERLAY_Y] = y
        }
    }

    suspend fun setRepeatCallWindowMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_REPEAT_CALL_WINDOW] = AppSettings.clampRepeatCallWindow(minutes)
        }
    }

    suspend fun setStatsPeriodMonths(months: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_STATS_PERIOD] = AppSettings.clampStatsPeriod(months)
        }
    }

    suspend fun setNormalizeNumbers(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_NORMALIZE_NUMBERS] = enabled }
    }

    suspend fun setShowOverlayOnOutgoing(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SHOW_OUTGOING_OVERLAY] = enabled }
    }

    suspend fun setMonitorEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_MONITOR_ENABLED] = enabled }
    }

    private fun readablePreferences(): Flow<Preferences> = dataStore.data.catch { throwable ->
        // A corrupted or unreadable file should fall back to defaults instead of crashing.
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    private companion object {
        val KEY_REPEAT_CALL_WINDOW = intPreferencesKey("repeat_call_window_minutes")
        val KEY_STATS_PERIOD = intPreferencesKey("stats_period_months")
        val KEY_NORMALIZE_NUMBERS = booleanPreferencesKey("normalize_numbers")
        val KEY_SHOW_OUTGOING_OVERLAY = booleanPreferencesKey("show_overlay_on_outgoing")
        val KEY_MONITOR_ENABLED = booleanPreferencesKey("monitor_enabled")
        val KEY_OVERLAY_X = intPreferencesKey("overlay_x")
        val KEY_OVERLAY_Y = intPreferencesKey("overlay_y")
    }
}
