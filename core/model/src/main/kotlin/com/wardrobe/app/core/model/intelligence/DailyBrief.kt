package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.weather.WeatherSnapshot

/**
 * Home's "Good Morning {name}" moment (Phase 9) — every field here is either
 * already-computed data (greeting, weather, today's recommendation) or a
 * short list of sentences built from the recommendation engine's own
 * per-candidate scoring reasons (`core:data`'s `ScoredCandidate.reasons`,
 * Phase 6) — Constitution: never a canned or random sentence.
 * [todaysOccasionName] and [recommendation] are `null`
 * when there's genuinely nothing to report (no planned occasion today, or
 * an empty wardrobe) — never a placeholder value.
 */
data class DailyBrief(
    val greeting: String,
    val weather: WeatherSnapshot?,
    val todaysOccasionName: String?,
    val recommendation: ScoredOutfit?,
    val explanations: List<String>,
)
