package com.wardrobe.app.core.network.weather

import retrofit2.http.GET
import retrofit2.http.Query

private const val CURRENT_FIELDS =
    "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
private const val DAILY_FIELDS =
    "temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
        "precipitation_probability_max,uv_index_max,weather_code,wind_speed_10m_max"

/** The single outbound network call this app makes (phase-1-architecture.md
 * Section 18) — Open-Meteo, free, no API key. [forecastDays] defaults to a
 * week so a calendar-planned outfit a few days out can still resolve real
 * forecast data, not just "today." */
interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = DEFAULT_FORECAST_DAYS,
    ): OpenMeteoResponseDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val DEFAULT_FORECAST_DAYS = 7
    }
}
