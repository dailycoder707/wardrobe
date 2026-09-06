package com.wardrobe.app.feature.outfits.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository.Companion.DEFAULT_SUGGESTION_COUNT
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val NANOS_PER_MILLI = 1_000_000L
private const val SHOW_ANOTHER_STEP = 3
private const val MAX_SUGGESTION_COUNT = 9
private const val GENERATION_FAILED_MESSAGE = "Couldn't generate a recommendation. Try again."

internal data class ReferenceData(
    val garmentsById: Map<GarmentId, Garment>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
)

@Suppress("LongParameterList")
@HiltViewModel
class RecommendationsViewModel
    @Inject
    constructor(
        private val stylingEngineRepository: StylingEngineRepository,
        private val garmentRepository: GarmentRepository,
        private val categoryRepository: CategoryRepository,
        private val brandRepository: BrandRepository,
        private val outfitRepository: OutfitRepository,
        private val wearEventRepository: WearEventRepository,
        private val styleRuleRepository: StyleRuleRepository,
        private val occasionRepository: OccasionRepository,
        private val weatherRepository: WeatherRepository,
        private val aiProviderSettingsRepository: AiProviderSettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(RecommendationsUiState())
        val uiState: StateFlow<RecommendationsUiState> = mutableUiState.asStateFlow()

        /** How many outfits the *next* fetch requests — grows via [showAnother],
         * resets to the default on any fresh [generate] (a real context
         * change deserves a clean slate, not an ever-growing request). */
        private var requestedCount = DEFAULT_SUGGESTION_COUNT

        init {
            generate()
            viewModelScope.launch { loadOccasions(occasionRepository, mutableUiState) }
            viewModelScope.launch { observeCloudStylingActivity(aiProviderSettingsRepository, mutableUiState) }
        }

        fun generate() {
            requestedCount = DEFAULT_SUGGESTION_COUNT
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, isError = false, actionMessage = null) }
                fetchAndApply(previousSignatures = emptySet(), isFreshGeneration = true)
            }
        }

        /** M19's real "Show another" — requests more candidates from the
         * *same* engine (never a second engine, never randomness) and only
         * changes what's on screen if a genuinely different outfit turns up
         * beyond what's already visible. If nothing new exists (the
         * wardrobe's own candidate pool is exhausted for this context), says
         * so honestly instead of silently re-showing the same outfit. */
        fun showAnother() {
            val current = mutableUiState.value
            if (current.suggestions.isEmpty()) {
                generate()
                return
            }
            val previousSignatures = current.suggestions.map { it.garmentSignature() }.toSet()
            requestedCount = (requestedCount + SHOW_ANOTHER_STEP).coerceAtMost(MAX_SUGGESTION_COUNT)
            viewModelScope.launch {
                mutableUiState.update { it.copy(actionMessage = null) }
                fetchAndApply(previousSignatures, isFreshGeneration = false)
            }
        }

        fun onOccasionSelected(occasionId: OccasionId?) {
            if (occasionId == mutableUiState.value.selectedOccasionId) return
            mutableUiState.update { it.copy(selectedOccasionId = occasionId) }
            generate()
        }

        /** A recommendation-engine call is a genuine failure boundary: cloud
         * parsing, weather lookup, or the on-device engine can each fail for
         * reasons this screen can't enumerate in advance, and every one of
         * them must land the user on an honest error state rather than crash
         * the ViewModel. Matches the same broad-catch-at-a-boundary precedent
         * already used in WeatherRepositoryImpl/SyncEngine/SyncRepositoryImpl. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun fetchAndApply(
            previousSignatures: Set<Set<Long>>,
            isFreshGeneration: Boolean,
        ) {
            try {
                val start = System.nanoTime()
                val weather = weatherRepository.getForecastForConfiguredLocation(LocalDate.now(clock))
                val context = buildSuggestionContext(clock, weather, mutableUiState.value.selectedOccasionId)
                val scored = stylingEngineRepository.suggestOutfits(context, requestedCount)
                val elapsedMillis = (System.nanoTime() - start) / NANOS_PER_MILLI
                val ref = loadReferenceData(garmentRepository, categoryRepository, brandRepository)
                val uiModels = scored.map { it.toUiModel(ref.garmentsById, ref.categoriesById, ref.brandsById) }
                recordDiagnostics(stylingEngineRepository, styleRuleRepository, elapsedMillis, uiModels)

                mutableUiState.update { current ->
                    if (isFreshGeneration) {
                        applyFreshGeneration(current, uiModels, weather, ref.garmentsById.isEmpty())
                    } else {
                        applyShowAnotherResult(current, uiModels, previousSignatures)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                mutableUiState.update {
                    it.copy(isLoading = false, isError = true, errorMessage = GENERATION_FAILED_MESSAGE)
                }
            }
        }

        fun selectSuggestion(index: Int) {
            mutableUiState.update { it.copy(selectedIndex = index) }
        }

        fun replaceSlot(slot: OutfitSlot) {
            val selected = mutableUiState.value.selected ?: return
            viewModelScope.launch {
                val context = buildSuggestionContext(clock, null, mutableUiState.value.selectedOccasionId)
                val replacementId = stylingEngineRepository.suggestReplacementForSlot(selected.outfit, slot, context)
                if (replacementId == null) {
                    mutableUiState.update { it.copy(actionMessage = "Nothing else available for that slot right now") }
                    return@launch
                }
                val updated = replaceSlotInUiModel(selected, slot, replacementId)
                val ref = loadReferenceData(garmentRepository, categoryRepository, brandRepository)
                val updatedUiModel = updated.toUiModel(ref.garmentsById, ref.categoriesById, ref.brandsById)
                mutableUiState.update { state ->
                    val updatedList = state.suggestions.toMutableList()
                    if (state.selectedIndex in updatedList.indices) updatedList[state.selectedIndex] = updatedUiModel
                    state.copy(suggestions = updatedList)
                }
            }
        }

        fun favoriteSelected() =
            withPersistedSelection(viewModelScope, outfitRepository, mutableUiState, "Added to favorites") { outfitId ->
                outfitRepository.setFavorite(outfitId, true)
            }

        fun saveSelected() =
            withPersistedSelection(viewModelScope, outfitRepository, mutableUiState, "Saved to your looks") {}

        fun wearToday() =
            withPersistedSelection(viewModelScope, outfitRepository, mutableUiState, "Logged for today") { outfitId ->
                logOutfitWear(wearEventRepository, clock, outfitId, LocalDate.now(clock))
            }

        fun schedule(date: LocalDate) =
            withPersistedSelection(
                viewModelScope,
                outfitRepository,
                mutableUiState,
                "Scheduled for $date",
            ) { outfitId ->
                logOutfitWear(wearEventRepository, clock, outfitId, date)
            }
    }
