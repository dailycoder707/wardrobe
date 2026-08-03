package com.wardrobe.app.feature.closet.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.feature.closet.navigation.GarmentDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val EPOCH_YEAR = 1970
private const val EPOCH_MONTH = 1
private const val EPOCH_DAY = 1
private val HISTORY_START_DATE: LocalDate = LocalDate.of(EPOCH_YEAR, EPOCH_MONTH, EPOCH_DAY)

@HiltViewModel
class GarmentDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val garmentRepository: GarmentRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
        wearEventRepository: WearEventRepository,
    ) : ViewModel() {
        private val garmentId = GarmentId(savedStateHandle.toRoute<GarmentDetailRoute>().garmentId)

        private val deleteBlockedMessageFlow = MutableStateFlow<String?>(null)
        val deleteBlockedMessage: StateFlow<String?> = deleteBlockedMessageFlow.asStateFlow()

        val uiState: StateFlow<GarmentDetailUiState> =
            combine(
                garmentRepository.observeGarment(garmentId),
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                wardrobeIntelligenceRepository.observeGarmentInsights(garmentId),
                wearEventRepository.observeEvents(DateRange(HISTORY_START_DATE, LocalDate.now())),
            ) { garment, categories, brands, insights, wearEvents ->
                if (garment == null) {
                    GarmentDetailUiState(isLoading = false, notFound = true)
                } else {
                    GarmentDetailUiState(
                        isLoading = false,
                        garment = garment,
                        categoryName = categories.firstOrNull { it.id == garment.categoryId }?.name,
                        brandName = garment.brandId?.let { id -> brands.firstOrNull { it.id == id }?.name },
                        insights = insights,
                        wearHistory =
                            wearEvents
                                .filter { it.garmentId == garmentId }
                                .map { it.date }
                                .sortedDescending(),
                    )
                }
            }.catch { throwable ->
                emit(GarmentDetailUiState(isLoading = false, error = throwable.message ?: "Something went wrong."))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = GarmentDetailUiState(isLoading = true),
            )

        fun onToggleFavorite() {
            val current = uiState.value.garment ?: return
            viewModelScope.launch { garmentRepository.setFavorite(garmentId, !current.isFavorite) }
        }

        /** Phase 9 — surfaces the "don't recommend right now" flag
         * (`Garment.isInLaundry`) that has existed since Phase 6 but was
         * never toggleable in any UI until now. */
        fun onToggleLaundry() {
            val current = uiState.value.garment ?: return
            viewModelScope.launch { garmentRepository.setInLaundry(garmentId, !current.isInLaundry) }
        }

        fun onDelete(onDeleted: () -> Unit) {
            viewModelScope.launch {
                runCatching { garmentRepository.deleteGarment(garmentId) }
                    .onSuccess { onDeleted() }
                    .onFailure {
                        // RESTRICT foreign key — wear/outfit history exists (phase-3-persistence.md).
                        deleteBlockedMessageFlow.value =
                            "This item has wear history and can't be deleted. " +
                            "Try marking it as Sold or Donating instead."
                    }
            }
        }

        fun onDeleteBlockedMessageShown() {
            deleteBlockedMessageFlow.value = null
        }
    }
