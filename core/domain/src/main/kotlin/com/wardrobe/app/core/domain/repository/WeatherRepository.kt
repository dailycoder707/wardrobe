package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.weather.WeatherSnapshot
import java.time.LocalDate

data class Location(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Always returns a value — never a bare error (phase-1-architecture.md Section 18).
 * On network failure, the implementation (Phase 7, `core:data`) returns the last
 * cached [WeatherSnapshot] with `isStale = true` rather than propagating an
 * exception, so a screen can render "using yesterday's forecast" instead of a dead end.
 */
interface WeatherRepository {
    suspend fun getForecast(
        location: Location,
        date: LocalDate,
    ): WeatherSnapshot

    /**
     * Resolves *where* from Weather Settings (device location, falling back
     * to a manually-set one) and then delegates to [getForecast] — the
     * convenience most callers actually want (Home's Weather Card, the
     * recommendation engine's own context resolution). Returns `null` rather
     * than a snapshot when weather is turned off in Weather Settings or no
     * location is resolvable at all — every caller treats that exactly like
     * "no weather," never an error (Constitution rule 12).
     */
    suspend fun getForecastForConfiguredLocation(date: LocalDate): WeatherSnapshot?
}
