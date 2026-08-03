package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherPreferencesDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observePreferences(): Flow<WeatherPreferences> =
            dataStore.data.map { prefs ->
                val defaults = WeatherPreferences()
                WeatherPreferences(
                    useWeather = prefs[PreferenceKeys.WEATHER_USE_WEATHER] ?: defaults.useWeather,
                    offlineOnly = prefs[PreferenceKeys.WEATHER_OFFLINE_ONLY] ?: defaults.offlineOnly,
                    useDeviceLocation =
                        prefs[PreferenceKeys.WEATHER_USE_DEVICE_LOCATION] ?: defaults.useDeviceLocation,
                    manualLatitude = prefs[PreferenceKeys.WEATHER_MANUAL_LATITUDE],
                    manualLongitude = prefs[PreferenceKeys.WEATHER_MANUAL_LONGITUDE],
                    manualLocationLabel = prefs[PreferenceKeys.WEATHER_MANUAL_LOCATION_LABEL],
                    temperatureUnit = decodeTemperatureUnit(prefs[PreferenceKeys.WEATHER_TEMPERATURE_UNIT]),
                    refreshIntervalHours =
                        prefs[PreferenceKeys.WEATHER_REFRESH_INTERVAL_HOURS] ?: defaults.refreshIntervalHours,
                )
            }

        suspend fun setPreferences(preferences: WeatherPreferences) {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.WEATHER_USE_WEATHER] = preferences.useWeather
                prefs[PreferenceKeys.WEATHER_OFFLINE_ONLY] = preferences.offlineOnly
                prefs[PreferenceKeys.WEATHER_USE_DEVICE_LOCATION] = preferences.useDeviceLocation
                val latitude = preferences.manualLatitude
                if (latitude != null) {
                    prefs[PreferenceKeys.WEATHER_MANUAL_LATITUDE] = latitude
                } else {
                    prefs.remove(PreferenceKeys.WEATHER_MANUAL_LATITUDE)
                }
                val longitude = preferences.manualLongitude
                if (longitude != null) {
                    prefs[PreferenceKeys.WEATHER_MANUAL_LONGITUDE] = longitude
                } else {
                    prefs.remove(PreferenceKeys.WEATHER_MANUAL_LONGITUDE)
                }
                val locationLabel = preferences.manualLocationLabel
                if (locationLabel != null) {
                    prefs[PreferenceKeys.WEATHER_MANUAL_LOCATION_LABEL] = locationLabel
                } else {
                    prefs.remove(PreferenceKeys.WEATHER_MANUAL_LOCATION_LABEL)
                }
                prefs[PreferenceKeys.WEATHER_TEMPERATURE_UNIT] = preferences.temperatureUnit.name
                prefs[PreferenceKeys.WEATHER_REFRESH_INTERVAL_HOURS] = preferences.refreshIntervalHours
            }
        }

        private fun decodeTemperatureUnit(raw: String?): TemperatureUnit =
            raw?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() } ?: TemperatureUnit.CELSIUS
    }
