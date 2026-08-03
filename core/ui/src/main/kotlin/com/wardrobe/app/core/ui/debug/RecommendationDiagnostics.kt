package com.wardrobe.app.core.ui.debug

import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.WeatherSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecommendationDiagnosticsSnapshot(
    val lastGenerationTimeMillis: Long = 0,
    val lastSuggestionCount: Int = 0,
    val lastTopScore: Double = 0.0,
    val activeRuleCount: Int = 0,
    val activeFlowSubscriptions: Int = 0,
    val weatherSource: WeatherSource = WeatherSource.NONE,
    val weatherCacheAgeMinutes: Long? = null,
    val rulesAppliedCount: Int = 0,
    val plannedOutfitUsed: Boolean = false,
    val contextNotes: List<String> = emptyList(),
)

/**
 * Phase 6's Developer Panel window into the styling engine, extended Phase 7
 * with context diagnostics (weather source/cache age, rules applied,
 * planned-outfit use, context notes). The rule engine itself lives in
 * `core:data` (`StylingEngineRepositoryImpl`), which has no `core:ui`
 * dependency (same layering rule Phase 5e's `StatsDiagnostics` established)
 * — so `RecommendationsViewModel` (`feature:outfits`) is what reports here,
 * timing its own call to `suggestOutfits`/`suggestForItem` and reading
 * `StylingEngineRepository.lastRunDiagnostics()` immediately after.
 */
object RecommendationDiagnostics {
    private val mutableState = MutableStateFlow(RecommendationDiagnosticsSnapshot())
    val state: StateFlow<RecommendationDiagnosticsSnapshot> = mutableState.asStateFlow()

    /** [runDiagnostics] bundles Phase 7's context fields into one parameter
     * — `StylingEngineRepository.lastRunDiagnostics()`'s own return type —
     * specifically so this function's own parameter count stays under
     * detekt's `LongParameterList` threshold regardless of how much context
     * diagnostics grows. */
    fun recordGeneration(
        generationTimeMillis: Long,
        suggestionCount: Int,
        topScore: Double,
        activeRuleCount: Int,
        runDiagnostics: RecommendationRunDiagnostics = RecommendationRunDiagnostics(),
    ) {
        mutableState.update {
            it.copy(
                lastGenerationTimeMillis = generationTimeMillis,
                lastSuggestionCount = suggestionCount,
                lastTopScore = topScore,
                activeRuleCount = activeRuleCount,
                weatherSource = runDiagnostics.weatherSource,
                weatherCacheAgeMinutes = runDiagnostics.weatherCacheAgeMinutes,
                rulesAppliedCount = runDiagnostics.rulesAppliedCount,
                plannedOutfitUsed = runDiagnostics.plannedOutfitUsed,
                contextNotes = runDiagnostics.contextNotes,
            )
        }
    }

    fun onSubscribed() {
        mutableState.update { it.copy(activeFlowSubscriptions = it.activeFlowSubscriptions + 1) }
    }

    fun onUnsubscribed() {
        mutableState.update {
            it.copy(activeFlowSubscriptions = (it.activeFlowSubscriptions - 1).coerceAtLeast(0))
        }
    }

    fun reset() {
        mutableState.value = RecommendationDiagnosticsSnapshot()
    }
}
