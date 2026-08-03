package com.wardrobe.app.core.network.weather

import com.wardrobe.app.core.model.weather.WeatherCondition
import java.time.LocalDate

// Open-Meteo's own WMO weather-code table (https://open-meteo.com/en/docs) —
// named per its official description, not left as raw literals, per this
// project's "named constants, not magic numbers" convention.
private const val WMO_CLEAR_SKY = 0
private const val WMO_MAINLY_CLEAR = 1
private const val WMO_PARTLY_CLOUDY = 2
private const val WMO_OVERCAST = 3
private const val WMO_FOG = 45
private const val WMO_DEPOSITING_RIME_FOG = 48
private const val WMO_DRIZZLE_LIGHT = 51
private const val WMO_DRIZZLE_MODERATE = 53
private const val WMO_DRIZZLE_DENSE = 55
private const val WMO_FREEZING_DRIZZLE_LIGHT = 56
private const val WMO_FREEZING_DRIZZLE_DENSE = 57
private const val WMO_RAIN_SLIGHT = 61
private const val WMO_RAIN_MODERATE = 63
private const val WMO_RAIN_HEAVY = 65
private const val WMO_FREEZING_RAIN_LIGHT = 66
private const val WMO_FREEZING_RAIN_HEAVY = 67
private const val WMO_SNOW_SLIGHT = 71
private const val WMO_SNOW_MODERATE = 73
private const val WMO_SNOW_HEAVY = 75
private const val WMO_SNOW_GRAINS = 77
private const val WMO_RAIN_SHOWERS_SLIGHT = 80
private const val WMO_RAIN_SHOWERS_MODERATE = 81
private const val WMO_RAIN_SHOWERS_VIOLENT = 82
private const val WMO_SNOW_SHOWERS_SLIGHT = 85
private const val WMO_SNOW_SHOWERS_HEAVY = 86
private const val WMO_THUNDERSTORM = 95
private const val WMO_THUNDERSTORM_SLIGHT_HAIL = 96
private const val WMO_THUNDERSTORM_HEAVY_HAIL = 99

private val CLOUDY_CODES = setOf(WMO_MAINLY_CLEAR, WMO_PARTLY_CLOUDY, WMO_OVERCAST)
private val FOG_CODES = setOf(WMO_FOG, WMO_DEPOSITING_RIME_FOG)
private val RAIN_CODES =
    setOf(
        WMO_DRIZZLE_LIGHT,
        WMO_DRIZZLE_MODERATE,
        WMO_DRIZZLE_DENSE,
        WMO_FREEZING_DRIZZLE_LIGHT,
        WMO_FREEZING_DRIZZLE_DENSE,
        WMO_RAIN_SLIGHT,
        WMO_RAIN_MODERATE,
        WMO_RAIN_HEAVY,
        WMO_FREEZING_RAIN_LIGHT,
        WMO_FREEZING_RAIN_HEAVY,
        WMO_RAIN_SHOWERS_SLIGHT,
        WMO_RAIN_SHOWERS_MODERATE,
        WMO_RAIN_SHOWERS_VIOLENT,
    )
private val SNOW_CODES =
    setOf(
        WMO_SNOW_SLIGHT,
        WMO_SNOW_MODERATE,
        WMO_SNOW_HEAVY,
        WMO_SNOW_GRAINS,
        WMO_SNOW_SHOWERS_SLIGHT,
        WMO_SNOW_SHOWERS_HEAVY,
    )
private val STORM_CODES = setOf(WMO_THUNDERSTORM, WMO_THUNDERSTORM_SLIGHT_HAIL, WMO_THUNDERSTORM_HEAVY_HAIL)

/** Open-Meteo's own WMO weather-code table, mapped down to [WeatherCondition]
 * — a provider-specific detail that belongs at this network boundary, not
 * leaked into `core:model`/`core:domain`. Unrecognized/missing codes map to
 * [WeatherCondition.UNKNOWN], never a guess. */
internal fun wmoCodeToCondition(code: Int?): WeatherCondition =
    when (code) {
        null -> WeatherCondition.UNKNOWN
        WMO_CLEAR_SKY -> WeatherCondition.SUNNY
        in CLOUDY_CODES -> WeatherCondition.CLOUDY
        in FOG_CODES -> WeatherCondition.FOG
        in RAIN_CODES -> WeatherCondition.RAIN
        in SNOW_CODES -> WeatherCondition.SNOW
        in STORM_CODES -> WeatherCondition.STORM
        else -> WeatherCondition.UNKNOWN
    }

/** Real Retrofit-backed [WeatherProvider] — Open-Meteo, this app's only
 * outbound network call (phase-1-architecture.md Section 18). Every field is
 * read defensively (`getOrNull`) since Open-Meteo's daily arrays can be
 * shorter than requested near the edge of its own forecast horizon. */
class OpenMeteoWeatherProvider(
    private val service: OpenMeteoService,
) : WeatherProvider {
    override suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
    ): List<WeatherObservation> {
        val response = service.getForecast(latitude, longitude)
        val daily = response.daily ?: return emptyList()
        return daily.time.indices.mapNotNull { index -> toObservation(index, daily, response) }
    }

    private fun toObservation(
        index: Int,
        daily: OpenMeteoDailyDto,
        response: OpenMeteoResponseDto,
    ): WeatherObservation? {
        val date = daily.time.getOrNull(index)?.let(LocalDate::parse) ?: return null
        val dailyCode = daily.weatherCode.getOrNull(index)
        val isToday = index == 0
        return WeatherObservation(
            date = date,
            currentTempC = response.current?.temperature2m.takeIf { isToday },
            feelsLikeC = response.current?.apparentTemperature.takeIf { isToday },
            humidityPercent = response.current?.relativeHumidity2m.takeIf { isToday },
            windSpeedKph = if (isToday) response.current?.windSpeed10m else daily.windSpeed10mMax.getOrNull(index),
            uvIndex = daily.uvIndexMax.getOrNull(index),
            precipitationProbabilityPercent = daily.precipitationProbabilityMax.getOrNull(index),
            condition = wmoCodeToCondition(if (isToday) response.current?.weatherCode ?: dailyCode else dailyCode),
            conditionCode = dailyCode?.toString(),
            tempHighC = daily.temperature2mMax.getOrNull(index),
            tempLowC = daily.temperature2mMin.getOrNull(index),
            apparentTempHighC = daily.apparentTemperatureMax.getOrNull(index),
            apparentTempLowC = daily.apparentTemperatureMin.getOrNull(index),
        )
    }
}
