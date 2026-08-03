package com.wardrobe.app.core.model.styling

/** Where the weather signal used in the most recent recommendation run came
 * from — surfaced by the Developer Panel (Phase 7), never shown to an
 * ordinary user. */
enum class WeatherSource { LIVE, CACHED, NONE }

/**
 * A snapshot of *how* the most recent [StylingEngineRepository] call resolved
 * its context — Developer Panel diagnostics only (phase-7-context-aware-
 * assistant.md), never user-facing copy (that's [ScoredOutfit.explanation]'s
 * job). [rulesEvaluatedCount]/[rulesAppliedCount] cover only the per-garment
 * `AVOID_CATEGORY`/`AVOID_BRAND` rules `RecommendationRuleEngine` filters
 * candidates with — whole-outfit rules (`AVOID_COLOR_COMBO`/
 * `ALWAYS_INCLUDE_CATEGORY`) aren't separately counted here, a stated
 * simplification, not an omission.
 */
data class RecommendationRunDiagnostics(
    val weatherSource: WeatherSource = WeatherSource.NONE,
    val weatherCacheAgeMinutes: Long? = null,
    val rulesEvaluatedCount: Int = 0,
    val rulesAppliedCount: Int = 0,
    val plannedOutfitUsed: Boolean = false,
    val contextNotes: List<String> = emptyList(),
)
