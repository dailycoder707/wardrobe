package com.wardrobe.app.core.data.repository.weather

import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves *where* to fetch weather for — device's last-known coarse location
 * when [com.wardrobe.app.core.model.weather.WeatherPreferences.useDeviceLocation]
 * is on and available, otherwise the manually-set coordinates from Weather
 * Settings (phase-1-architecture.md Section 18). Returns `null` when neither
 * is available — callers treat that exactly like "no weather," never an
 * error (Constitution rule 12, Context Refinement Rule).
 */
@Singleton
class WeatherLocationResolver
    @Inject
    constructor(
        private val deviceLocationSource: DeviceLocationSource,
        private val weatherPreferencesRepository: WeatherPreferencesRepository,
    ) {
        suspend fun resolve(): Location? {
            val prefs = weatherPreferencesRepository.observePreferences().first()
            val deviceLocation = if (prefs.useDeviceLocation) deviceLocationSource.lastKnownLocation() else null
            if (deviceLocation != null) return deviceLocation
            val latitude = prefs.manualLatitude
            val longitude = prefs.manualLongitude
            return if (latitude != null && longitude != null) Location(latitude, longitude) else null
        }
    }
