package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.datastore.preferences.WeatherPreferencesDataStore
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.model.weather.WeatherPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WeatherPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: WeatherPreferencesDataStore,
    ) : WeatherPreferencesRepository {
        override fun observePreferences(): Flow<WeatherPreferences> = dataStore.observePreferences()

        override suspend fun setPreferences(preferences: WeatherPreferences) = dataStore.setPreferences(preferences)
    }
