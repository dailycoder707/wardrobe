package com.wardrobe.app.core.model.styling

import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import java.time.LocalDate

/**
 * Everything the Phase 6 styling engine needs to score suggestions for a given moment:
 * today's (or a trip day's) weather, an optional occasion, and the date being planned
 * for. Deliberately minimal here — Phase 6 owns the scoring algorithm itself; this is
 * just the input shape the repository interface needs to compile against now.
 */
data class SuggestionContext(
    val date: LocalDate,
    val weather: WeatherSnapshot?,
    val occasionId: OccasionId?,
)
