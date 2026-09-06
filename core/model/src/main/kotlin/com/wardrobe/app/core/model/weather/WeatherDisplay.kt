package com.wardrobe.app.core.model.weather

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private const val FAHRENHEIT_SCALE_NUMERATOR = 9
private const val FAHRENHEIT_SCALE_DENOMINATOR = 5
private const val FAHRENHEIT_OFFSET = 32

/**
 * "Updated 2 hours ago" / "Updated yesterday" / "No weather available" — the
 * three states the brief's own examples name. A `null` receiver (no snapshot
 * exists at all, e.g. first launch with no network yet) and a snapshot whose
 * fields are all null (the empty stale snapshot `WeatherRepositoryImpl`
 * returns when nothing was ever cached) both read as "No weather available."
 */
fun WeatherSnapshot?.updatedAtLabel(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (this == null || (currentTempC == null && tempHighC == null)) return "No weather available"
    val elapsed = Duration.between(fetchedAt, now)
    val fetchedDate = fetchedAt.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        elapsed.toHours() < 1 -> "Updated just now"
        fetchedDate == today -> "Updated ${elapsed.toHours()} hour${if (elapsed.toHours() == 1L) "" else "s"} ago"
        fetchedDate == today.minusDays(1) -> "Updated yesterday"
        else -> "Updated ${Duration.between(fetchedAt, now).toDays()} days ago"
    }
}

/** "22°C today. Light rain expected this afternoon." — the Home-screen
 * assistant greeting's weather line. Never technical wording, matching the
 * recommendation explainer's own "no black-box" convention. */
fun WeatherSnapshot.headline(unit: TemperatureUnit): String {
    val current = currentTempC?.let { "${displayTemp(it, unit)} today." }
    val conditionPhrase = condition?.let(::conditionPhrase)
    return listOfNotNull(current, conditionPhrase).joinToString(" ").ifBlank { "Weather details aren't available." }
}

/** [headline]'s counterpart for a date that only has a daily forecast (high/
 * low) rather than a live current reading — every calendar date other than
 * today (M20). [headline] itself always reads "Weather details aren't
 * available." for these snapshots since [WeatherSnapshot.currentTempC] is
 * `null` whenever the forecast entry isn't for today; this builds an
 * equally honest line from [WeatherSnapshot.tempLowC]/[WeatherSnapshot.tempHighC]
 * instead, e.g. "18°C–24°C. Rain expected." */
fun WeatherSnapshot.dailyHeadline(unit: TemperatureUnit): String {
    val range =
        when {
            tempLowC != null && tempHighC != null -> "${displayTemp(tempLowC, unit)}–${displayTemp(tempHighC, unit)}"
            tempHighC != null -> displayTemp(tempHighC, unit)
            tempLowC != null -> displayTemp(tempLowC, unit)
            else -> null
        }
    val conditionPhrase = condition?.let(::conditionPhrase)
    return listOfNotNull(range, conditionPhrase).joinToString(" ").ifBlank { "Weather details aren't available." }
}

fun displayTemp(
    celsius: Double,
    unit: TemperatureUnit,
): String =
    when (unit) {
        TemperatureUnit.CELSIUS -> {
            "${celsius.roundToInt()}°C"
        }

        TemperatureUnit.FAHRENHEIT -> {
            val fahrenheit = celsius * FAHRENHEIT_SCALE_NUMERATOR / FAHRENHEIT_SCALE_DENOMINATOR + FAHRENHEIT_OFFSET
            "${fahrenheit.roundToInt()}°F"
        }
    }

private fun conditionPhrase(condition: WeatherCondition): String =
    when (condition) {
        WeatherCondition.SUNNY -> "Clear skies expected."
        WeatherCondition.CLOUDY -> "Cloudy skies expected."
        WeatherCondition.RAIN -> "Rain expected."
        WeatherCondition.STORM -> "Storms expected — consider an umbrella."
        WeatherCondition.FOG -> "Foggy conditions expected."
        WeatherCondition.SNOW -> "Snow expected."
        WeatherCondition.UNKNOWN -> ""
    }
