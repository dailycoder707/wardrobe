package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.sync.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferencesDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observePreferences(): Flow<SyncPreferences> =
            dataStore.data.map { prefs ->
                val defaults = SyncPreferences()
                SyncPreferences(
                    autoSyncEnabled = prefs[PreferenceKeys.SYNC_AUTO_ENABLED] ?: defaults.autoSyncEnabled,
                    wifiOnly = prefs[PreferenceKeys.SYNC_WIFI_ONLY] ?: defaults.wifiOnly,
                    chargingOnly = prefs[PreferenceKeys.SYNC_CHARGING_ONLY] ?: defaults.chargingOnly,
                )
            }

        suspend fun setPreferences(preferences: SyncPreferences) {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_AUTO_ENABLED] = preferences.autoSyncEnabled
                prefs[PreferenceKeys.SYNC_WIFI_ONLY] = preferences.wifiOnly
                prefs[PreferenceKeys.SYNC_CHARGING_ONLY] = preferences.chargingOnly
            }
        }
    }
