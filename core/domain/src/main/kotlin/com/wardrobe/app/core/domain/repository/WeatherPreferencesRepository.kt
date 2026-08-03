package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.weather.WeatherPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Persists the Weather Settings screen's settings (Phase 7) — DataStore-
 * backed, mirrors [StylistPreferencesRepository]'s pattern exactly (one
 * shared `DataStore<Preferences>`, feature-scoped keys, the whole
 * [WeatherPreferences] value read/written together as one form).
 */
interface WeatherPreferencesRepository {
    fun observePreferences(): Flow<WeatherPreferences>

    suspend fun setPreferences(preferences: WeatherPreferences)
}
