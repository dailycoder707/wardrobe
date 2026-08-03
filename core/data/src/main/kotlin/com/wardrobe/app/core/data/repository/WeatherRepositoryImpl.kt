package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.repository.weather.WeatherLocationResolver
import com.wardrobe.app.core.data.repository.weather.emptyWeatherSnapshot
import com.wardrobe.app.core.data.repository.weather.toDomain
import com.wardrobe.app.core.data.repository.weather.toEntity
import com.wardrobe.app.core.database.dao.WeatherCacheDao
import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.core.network.weather.WeatherProvider
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val CACHE_COORDINATE_SCALE = 100.0

/**
 * Always returns a value, never a bare error (phase-1-architecture.md Section
 * 18): tries a live fetch first (unless [com.wardrobe.app.core.model.weather.WeatherPreferences.useWeather]
 * is off or [com.wardrobe.app.core.model.weather.WeatherPreferences.offlineOnly]
 * is on), falls back to the exact-date cache row, then the most recent cache
 * row for this location regardless of date, then a fully-empty `isStale =
 * true` snapshot if nothing was ever cached. Every one of these outcomes is
 * "weather is a documented pass-through" (Constitution rule 12) in action —
 * the caller (`StylingEngineRepositoryImpl`, a Home/Weather Card ViewModel)
 * never sees an exception from this class.
 */
class WeatherRepositoryImpl
    @Inject
    constructor(
        private val weatherProvider: WeatherProvider,
        private val weatherCacheDao: WeatherCacheDao,
        private val weatherPreferencesRepository: WeatherPreferencesRepository,
        private val weatherLocationResolver: WeatherLocationResolver,
        private val clock: Clock,
    ) : WeatherRepository {
        override suspend fun getForecast(
            location: Location,
            date: LocalDate,
        ): WeatherSnapshot {
            val latitude = roundForCache(location.latitude)
            val longitude = roundForCache(location.longitude)
            val prefs = weatherPreferencesRepository.observePreferences().first()

            if (prefs.useWeather && !prefs.offlineOnly) {
                fetchAndCache(latitude, longitude, date)?.let { return it }
            }

            return weatherCacheDao.get(latitude, longitude, date.toString())?.toDomain(isStale = true)
                ?: weatherCacheDao.getMostRecent(latitude, longitude)?.toDomain(isStale = true)
                ?: emptyWeatherSnapshot(latitude, longitude, date, clock.instant())
        }

        override suspend fun getForecastForConfiguredLocation(date: LocalDate): WeatherSnapshot? {
            val prefs = weatherPreferencesRepository.observePreferences().first()
            if (!prefs.useWeather) return null
            return weatherLocationResolver.resolve()?.let { getForecast(it, date) }
        }

        /**
         * Catches broadly on purpose — this function *is* the resilience
         * boundary Section 18 calls for. A network timeout, a malformed
         * Open-Meteo response, or any other provider failure must fall
         * through to the cache in [getForecast], never propagate as an
         * exception; this app has no `AppError`/`Result` error-handling
         * layer to route through instead (that Section 25 design was never
         * actually built in any prior phase — a pre-existing gap, not
         * introduced here).
         */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun fetchAndCache(
            latitude: Double,
            longitude: Double,
            date: LocalDate,
        ): WeatherSnapshot? =
            try {
                val fetchedAt = clock.instant()
                val observations = weatherProvider.fetchForecast(latitude, longitude)
                observations.forEach { observation ->
                    weatherCacheDao.upsert(observation.toEntity(latitude, longitude, fetchedAt))
                }
                observations
                    .firstOrNull { it.date == date }
                    ?.toEntity(latitude, longitude, fetchedAt)
                    ?.toDomain(isStale = false)
            } catch (e: Exception) {
                null
            }
    }

private fun roundForCache(value: Double): Double = Math.round(value * CACHE_COORDINATE_SCALE) / CACHE_COORDINATE_SCALE
