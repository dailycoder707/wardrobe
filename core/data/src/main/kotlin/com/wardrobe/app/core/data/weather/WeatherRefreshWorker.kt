package com.wardrobe.app.core.data.weather

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.weather.WeatherPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic, `NetworkType.CONNECTED` (phase-1-architecture.md Section 18 —
 * "not expedited, never worth waking radio/battery for"). Respects both
 * Weather Settings toggles by simply doing nothing (still `Result.success()`,
 * never a failure) when either is set — this worker is an optimization, not
 * a requirement: `WeatherRepositoryImpl` itself would make the same decision
 * on its next on-demand call regardless.
 */
@HiltWorker
class WeatherRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val weatherRepository: WeatherRepository,
        private val weatherPreferencesRepository: WeatherPreferencesRepository,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val prefs = weatherPreferencesRepository.observePreferences().first()
            if (!prefs.useWeather || prefs.offlineOnly) return Result.success()

            weatherRepository.getForecastForConfiguredLocation(LocalDate.now(clock))
            return Result.success()
        }

        companion object {
            private const val UNIQUE_WORK_NAME = "weather_refresh"

            fun schedule(
                workManager: WorkManager,
                intervalHours: Long = WeatherPreferences.DEFAULT_REFRESH_INTERVAL_HOURS.toLong(),
            ) {
                val request =
                    PeriodicWorkRequestBuilder<WeatherRefreshWorker>(intervalHours, TimeUnit.HOURS)
                        .addTag(WeatherRefreshWorker::class.java.simpleName)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                        ).build()
                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }
    }
