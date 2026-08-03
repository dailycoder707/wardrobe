package com.wardrobe.app.core.network.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo's `/v1/forecast` response shape (api.open-meteo.com,
 * phase-1-architecture.md Section 18) — only the `current`/`daily` fields
 * this app actually reads are declared; every other field Open-Meteo returns
 * is silently ignored by `Json { ignoreUnknownKeys = true }` (`core:data`'s
 * `NetworkModule`).
 */
@Serializable
data class OpenMeteoResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val current: OpenMeteoCurrentDto? = null,
    val daily: OpenMeteoDailyDto? = null,
)

@Serializable
data class OpenMeteoCurrentDto(
    @SerialName("temperature_2m") val temperature2m: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,
)

/** Every list here is parallel-indexed by day, `time[0]` being the earliest
 * forecast day in the request — `OpenMeteoWeatherProvider` picks the index
 * whose `time` entry matches the requested date. */
@Serializable
data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentTemperatureMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_min") val apparentTemperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeed10mMax: List<Double?> = emptyList(),
)
