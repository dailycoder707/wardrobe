package com.wardrobe.app.feature.settings.weather

import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRefreshScheduler
import com.wardrobe.app.core.model.weather.WeatherPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeWeatherPreferencesRepository(
    initial: WeatherPreferences = WeatherPreferences(),
) : WeatherPreferencesRepository {
    val flow = MutableStateFlow(initial)

    override fun observePreferences(): Flow<WeatherPreferences> = flow.asStateFlow()

    override suspend fun setPreferences(preferences: WeatherPreferences) {
        flow.value = preferences
    }
}

class FakeWeatherRefreshScheduler : WeatherRefreshScheduler {
    var lastRescheduledIntervalHours: Int? = null

    override fun reschedule(intervalHours: Int) {
        lastRescheduledIntervalHours = intervalHours
    }
}
