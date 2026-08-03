package com.wardrobe.app.core.model.weather

import com.wardrobe.app.core.model.common.WeatherCacheId
import java.time.Instant
import java.time.LocalDate

/**
 * Always returned by `WeatherRepository`, never a bare error (phase-1-architecture.md
 * Section 18) — [isStale] plus [fetchedAt] is how a screen renders a "using
 * yesterday's forecast" banner instead of a dead end when Open-Meteo is unreachable.
 *
 * [tempHighC]/[tempLowC]/[apparentTempHighC]/[apparentTempLowC] are [date]'s daily
 * forecast range (Phase 6's hard weather filter scores against these). [currentTempC]/
 * [feelsLikeC]/[humidityPercent]/[uvIndex]/[condition] are Phase 7's additions — the
 * live "right now" reading a Home-screen greeting or Weather Card actually displays.
 * [conditionCode] is the raw provider code (kept for debugging/Developer Panel use);
 * [condition] is the simple, already-mapped enum every screen renders instead.
 */
data class WeatherSnapshot(
    val id: WeatherCacheId,
    val latitude: Double,
    val longitude: Double,
    val date: LocalDate,
    val fetchedAt: Instant,
    val tempHighC: Double?,
    val tempLowC: Double?,
    val apparentTempHighC: Double?,
    val apparentTempLowC: Double?,
    val precipitationProbabilityPercent: Int?,
    val windSpeedKph: Double?,
    val conditionCode: String?,
    val isStale: Boolean,
    val currentTempC: Double? = null,
    val feelsLikeC: Double? = null,
    val humidityPercent: Int? = null,
    val uvIndex: Double? = null,
    val condition: WeatherCondition? = null,
)
