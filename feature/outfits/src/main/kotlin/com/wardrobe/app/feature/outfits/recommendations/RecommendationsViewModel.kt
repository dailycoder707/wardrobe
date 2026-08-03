package com.wardrobe.app.feature.outfits.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.ui.debug.RecommendationDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val NANOS_PER_MILLI = 1_000_000L

internal data class ReferenceData(
    val garmentsById: Map<GarmentId, Garment>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
)

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
        private val clock: Clock,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(RecommendationsUiState())
        val uiState: StateFlow<RecommendationsUiState> = mutableUiState.asStateFlow()

        init {
            generate()
        }

        fun generate() {
            viewModelScope.launch {
                mutableUiState.update { it.copy(isLoading = true, actionMessage = null) }
                val start = System.nanoTime()
                val scored = stylingEngineRepository.suggestOutfits(suggestionContext())
                val elapsedMillis = (System.nanoTime() - start) / NANOS_PER_MILLI
                val ref = loadReferenceData()
                val uiModels = scored.map { it.toUiModel(ref.garmentsById, ref.categoriesById, ref.brandsById) }
                val runDiagnostics = stylingEngineRepository.lastRunDiagnostics()
                RecommendationDiagnostics.recordGeneration(
                    generationTimeMillis = elapsedMillis,
                    suggestionCount = uiModels.size,
                    topScore = uiModels.maxOfOrNull { it.score } ?: 0.0,
                    activeRuleCount = styleRuleRepository.observeActiveRules().first().size,
                    runDiagnostics = runDiagnostics,
                )
                mutableUiState.update { it.copy(isLoading = false, suggestions = uiModels, selectedIndex = 0) }
            }
        }

        fun selectSuggestion(index: Int) {
            mutableUiState.update { it.copy(selectedIndex = index) }
        }

        fun replaceSlot(slot: OutfitSlot) {
            val selected = mutableUiState.value.selected ?: return
            viewModelScope.launch {
                val replacementId =
                    stylingEngineRepository.suggestReplacementForSlot(selected.outfit, slot, suggestionContext())
                if (replacementId == null) {
                    mutableUiState.update { it.copy(actionMessage = "Nothing else available for that slot right now") }
                    return@launch
                }
                val updated = replaceSlotInUiModel(selected, slot, replacementId)
                val ref = loadReferenceData()
                val updatedUiModel = updated.toUiModel(ref.garmentsById, ref.categoriesById, ref.brandsById)
                mutableUiState.update { state ->
                    val updatedList = state.suggestions.toMutableList()
                    if (state.selectedIndex in updatedList.indices) updatedList[state.selectedIndex] = updatedUiModel
                    state.copy(suggestions = updatedList)
                }
            }
        }

        fun favoriteSelected() =
            withPersistedSelection("Added to favorites") { outfitId ->
                outfitRepository.setFavorite(outfitId, true)
            }

        fun saveSelected() = withPersistedSelection("Saved to your looks") {}

        fun wearToday() =
            withPersistedSelection("Logged for today") { outfitId ->
                logOutfitWear(wearEventRepository, clock, outfitId, LocalDate.now(clock))
            }

        fun schedule(date: LocalDate) =
            withPersistedSelection("Scheduled for $date") { outfitId ->
                logOutfitWear(wearEventRepository, clock, outfitId, date)
            }

        private fun withPersistedSelection(
            message: String,
            afterSave: suspend (OutfitId) -> Unit,
        ) {
            val selected = mutableUiState.value.selected ?: return
            viewModelScope.launch {
                val savedId = persistSelectedOutfit(outfitRepository, selected)
                afterSave(savedId)
                mutableUiState.update { it.copy(actionMessage = message) }
            }
        }

        private suspend fun loadReferenceData(): ReferenceData =
            ReferenceData(
                garmentsById =
                    garmentRepository.observeGarments(GarmentFilter(status = null)).first().associateBy { it.id },
                categoriesById = categoryRepository.observeAll().first().associateBy { it.id },
                brandsById = brandRepository.observeAll().first().associateBy { it.id },
            )

        private fun suggestionContext(): SuggestionContext =
            SuggestionContext(date = LocalDate.now(clock), weather = null, occasionId = null)
    }
