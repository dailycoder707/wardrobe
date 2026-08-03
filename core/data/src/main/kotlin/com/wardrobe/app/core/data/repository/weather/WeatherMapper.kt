package com.wardrobe.app.core.data.repository.weather

import com.wardrobe.app.core.database.entity.WeatherCacheEntity
import com.wardrobe.app.core.model.common.WeatherCacheId
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.core.network.weather.WeatherObservation
import java.time.Instant
import java.time.LocalDate

internal fun WeatherCacheEntity.toDomain(isStale: Boolean): WeatherSnapshot =
    WeatherSnapshot(
        id = WeatherCacheId(id),
        latitude = latitude,
        longitude = longitude,
        date = LocalDate.parse(date),
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
        tempHighC = tempHighC,
        tempLowC = tempLowC,
        apparentTempHighC = apparentTempHighC,
        apparentTempLowC = apparentTempLowC,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        windSpeedKph = windSpeedKph,
        conditionCode = conditionCode,
        isStale = isStale,
        currentTempC = currentTempC,
        feelsLikeC = feelsLikeC,
        humidityPercent = humidityPercent,
        uvIndex = uvIndex,
        condition = condition?.let { runCatching { WeatherCondition.valueOf(it) }.getOrNull() },
    )

internal fun WeatherObservation.toEntity(
    latitude: Double,
    longitude: Double,
    fetchedAt: Instant,
): WeatherCacheEntity =
    WeatherCacheEntity(
        latitude = latitude,
        longitude = longitude,
        date = date.toString(),
        fetchedAt = fetchedAt.toEpochMilli(),
        tempHighC = tempHighC,
        tempLowC = tempLowC,
        apparentTempHighC = apparentTempHighC,
        apparentTempLowC = apparentTempLowC,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        windSpeedKph = windSpeedKph,
        conditionCode = conditionCode,
        currentTempC = currentTempC,
        feelsLikeC = feelsLikeC,
        humidityPercent = humidityPercent,
        uvIndex = uvIndex,
        condition = condition.name,
    )

/** The empty, all-`null`-fielded, `isStale = true` snapshot `WeatherRepositoryImpl`
 * returns when nothing has ever been cached for a location — "always returns
 * a value" (phase-1-architecture.md Section 18) even on a brand-new install
 * with no network yet. */
internal fun emptyWeatherSnapshot(
    latitude: Double,
    longitude: Double,
    date: LocalDate,
    fetchedAt: Instant,
): WeatherSnapshot =
    WeatherSnapshot(
        id = WeatherCacheId(0),
        latitude = latitude,
        longitude = longitude,
        date = date,
        fetchedAt = fetchedAt,
        tempHighC = null,
        tempLowC = null,
        apparentTempHighC = null,
        apparentTempLowC = null,
        precipitationProbabilityPercent = null,
        windSpeedKph = null,
        conditionCode = null,
        isStale = true,
        currentTempC = null,
        feelsLikeC = null,
        humidityPercent = null,
        uvIndex = null,
        condition = null,
    )
