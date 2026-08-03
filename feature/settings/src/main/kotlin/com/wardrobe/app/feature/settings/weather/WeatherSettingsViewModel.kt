package com.wardrobe.app.feature.settings.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRefreshScheduler
import com.wardrobe.app.core.model.weather.WeatherPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L

@HiltViewModel
class WeatherSettingsViewModel
    @Inject
    constructor(
        private val repository: WeatherPreferencesRepository,
        private val refreshScheduler: WeatherRefreshScheduler,
    ) : ViewModel() {
        val preferences: StateFlow<WeatherPreferences> =
            repository
                .observePreferences()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WeatherPreferences())

        /** Every edit reschedules the refresh worker at the (possibly
         * unchanged) interval — `WorkManager`'s `ExistingPeriodicWorkPolicy.UPDATE`
         * makes this a cheap no-op when the interval didn't actually change. */
        fun update(transform: (WeatherPreferences) -> WeatherPreferences) {
            viewModelScope.launch {
                val updated = transform(preferences.value)
                repository.setPreferences(updated)
                refreshScheduler.reschedule(updated.refreshIntervalHours)
            }
        }
    }
