package com.linkgrabber.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

private val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")

/**
 * Browsing history is off by default (privacy-first) and can be turned on
 * from the settings screen.
 */
class SettingsRepository(private val context: Context) {

    val historyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HISTORY_ENABLED] ?: false
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[HISTORY_ENABLED] = enabled }
    }
}
