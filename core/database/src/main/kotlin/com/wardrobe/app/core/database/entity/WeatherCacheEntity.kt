package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weather_cache",
    indices = [
        Index(value = ["latitude", "longitude", "date"], unique = true),
        Index("fetchedAt"),
    ],
)
data class WeatherCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val fetchedAt: Long,
    val tempHighC: Double?,
    val tempLowC: Double?,
    val apparentTempHighC: Double?,
    val apparentTempLowC: Double?,
    val precipitationProbabilityPercent: Int?,
    val windSpeedKph: Double?,
    val conditionCode: String?,
    val currentTempC: Double? = null,
    val feelsLikeC: Double? = null,
    val humidityPercent: Int? = null,
    val uvIndex: Double? = null,
    val condition: String? = null,
)
