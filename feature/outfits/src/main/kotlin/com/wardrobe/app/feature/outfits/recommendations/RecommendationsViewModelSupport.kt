package com.wardrobe.app.feature.outfits.recommendations

import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.core.model.weather.headline
import com.wardrobe.app.core.ui.debug.RecommendationDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

internal suspend fun loadReferenceData(
    garmentRepository: GarmentRepository,
    categoryRepository: CategoryRepository,
    brandRepository: BrandRepository,
): ReferenceData =
    ReferenceData(
        garmentsById = garmentRepository.observeGarments(GarmentFilter(status = null)).first().associateBy { it.id },
        categoriesById = categoryRepository.observeAll().first().associateBy { it.id },
        brandsById = brandRepository.observeAll().first().associateBy { it.id },
    )

internal fun buildSuggestionContext(
    clock: Clock,
    weather: WeatherSnapshot?,
    occasionId: OccasionId?,
) = SuggestionContext(
    date = LocalDate.now(clock),
    weather = weather,
    occasionId = occasionId,
)

internal suspend fun recordDiagnostics(
    stylingEngineRepository: StylingEngineRepository,
    styleRuleRepository: StyleRuleRepository,
    elapsedMillis: Long,
    uiModels: List<RecommendedOutfitUiModel>,
) {
    val runDiagnostics = stylingEngineRepository.lastRunDiagnostics()
    RecommendationDiagnostics.recordGeneration(
        generationTimeMillis = elapsedMillis,
        suggestionCount = uiModels.size,
        topScore = uiModels.maxOfOrNull { it.score } ?: 0.0,
        activeRuleCount = styleRuleRepository.observeActiveRules().first().size,
        runDiagnostics = runDiagnostics,
    )
}

internal suspend fun loadOccasions(
    occasionRepository: OccasionRepository,
    state: MutableStateFlow<RecommendationsUiState>,
) {
    val occasions = occasionRepository.observeAll().first()
    state.update { it.copy(availableOccasions = occasions) }
}

/** M18/M19 — the exact same live "is AI currently doing something" signal
 * Home uses, filtered to this screen's own capability. Never shown for the
 * rule-engine path, since that genuinely isn't AI. */
internal suspend fun observeCloudStylingActivity(
    aiProviderSettingsRepository: AiProviderSettingsRepository,
    state: MutableStateFlow<RecommendationsUiState>,
) {
    aiProviderSettingsRepository.observeActiveOperations().collect { operations ->
        val active = operations.any { it.capability == AiCapability.OUTFIT_STYLING }
        state.update { it.copy(isCloudStylingActive = active) }
    }
}

internal fun applyFreshGeneration(
    current: RecommendationsUiState,
    uiModels: List<RecommendedOutfitUiModel>,
    weather: WeatherSnapshot?,
    hasNoGarments: Boolean,
): RecommendationsUiState =
    current.copy(
        isLoading = false,
        isError = false,
        suggestions = uiModels,
        selectedIndex = 0,
        weatherSummary = weather?.headline(TemperatureUnit.CELSIUS),
        hasNoGarments = hasNoGarments,
    )

internal fun applyShowAnotherResult(
    current: RecommendationsUiState,
    uiModels: List<RecommendedOutfitUiModel>,
    previousSignatures: Set<Set<Long>>,
): RecommendationsUiState {
    val firstNewIndex = uiModels.indexOfFirst { it.garmentSignature() !in previousSignatures }
    return if (firstNewIndex == -1) {
        current.copy(actionMessage = NO_MORE_ALTERNATIVES_MESSAGE)
    } else {
        current.copy(suggestions = uiModels, selectedIndex = firstNewIndex)
    }
}

internal fun withPersistedSelection(
    scope: CoroutineScope,
    outfitRepository: OutfitRepository,
    state: MutableStateFlow<RecommendationsUiState>,
    message: String,
    afterSave: suspend (OutfitId) -> Unit,
) {
    val selected = state.value.selected ?: return
    scope.launch {
        val savedId = persistSelectedOutfit(outfitRepository, selected)
        afterSave(savedId)
        state.update { it.copy(actionMessage = message) }
    }
}
