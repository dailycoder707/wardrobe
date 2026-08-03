package com.wardrobe.app.core.network.weather

import com.wardrobe.app.core.model.weather.WeatherCondition
import java.time.LocalDate

/**
 * One day's worth of live weather data, as fetched — deliberately not
 * `WeatherSnapshot` itself: `id`/`fetchedAt`/`isStale` are cache/repository
 * concerns (`core:data`), not raw-fetch concerns. `null` for any field the
 * provider didn't return for that day, never a fabricated value.
 */
data class WeatherObservation(
    val date: LocalDate,
    val currentTempC: Double?,
    val feelsLikeC: Double?,
    val humidityPercent: Int?,
    val windSpeedKph: Double?,
    val uvIndex: Double?,
    val precipitationProbabilityPercent: Int?,
    val condition: WeatherCondition,
    val conditionCode: String?,
    val tempHighC: Double?,
    val tempLowC: Double?,
    val apparentTempHighC: Double?,
    val apparentTempLowC: Double?,
)

/**
 * Abstraction over "how do we actually fetch live weather" (Phase 7) —
 * mirrors `BackgroundRemover`'s own interface-over-one-implementation pattern
 * (ADR-008, Phase 5b): a future provider swap only touches this interface's
 * one implementation, never `WeatherRepository`/the recommendation engine.
 * Returns forecast days from [OpenMeteoService.DEFAULT_FORECAST_DAYS] worth
 * of data (today plus a lookahead window) so a calendar-planned future
 * outfit can resolve real forecast data, not just today's.
 */
interface WeatherProvider {
    suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
    ): List<WeatherObservation>
}
