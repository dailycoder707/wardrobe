package com.wardrobe.app.core.model.weather

/** Celsius is this schema's storage unit throughout (`WeatherSnapshot`); this
 * only controls *display* conversion, never what's persisted. */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/**
 * Weather Settings (Phase 7), persisted via `WeatherPreferencesRepository` →
 * `WeatherPreferencesDataStore`, mirroring `RecommendationPreferences`' own
 * "every field defaults so the app is useful before the user ever opens
 * Settings" convention.
 *
 * [useWeather] is the master toggle — when `false`, weather is never fetched,
 * never displayed, and never influences a recommendation, full stop.
 * [offlineOnly] is a narrower toggle *within* [useWeather]: still show/use
 * whatever is already cached, but never attempt a live network fetch (neither
 * from `WeatherRefreshWorker` nor an on-demand call) — for a user who wants
 * weather-aware styling from old data without ever letting the app touch the
 * network. [useDeviceLocation] `false`, or device location unavailable, falls
 * back to [manualLatitude]/[manualLongitude]/[manualLocationLabel] — see
 * `WeatherLocationResolver` (`core:data`). Geocoding a typed city name to
 * coordinates is deliberately not implemented: this app's network budget
 * (phase-1-architecture.md Section 0/18, `core:network`'s own build file
 * comment) is Open-Meteo and *only* Open-Meteo — adding a geocoding API call
 * would violate that budget decision, so manual location is entered as
 * coordinates, not a searchable place name.
 */
data class WeatherPreferences(
    val useWeather: Boolean = true,
    val offlineOnly: Boolean = false,
    val useDeviceLocation: Boolean = true,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualLocationLabel: String? = null,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_INTERVAL_HOURS,
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_HOURS = 3
    }
}
