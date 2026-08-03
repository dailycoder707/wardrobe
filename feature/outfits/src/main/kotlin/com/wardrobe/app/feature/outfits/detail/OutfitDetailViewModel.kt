package com.wardrobe.app.feature.outfits.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.feature.outfits.common.toTileUiModel
import com.wardrobe.app.feature.outfits.navigation.OutfitDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val EPOCH_YEAR = 1970
private val HISTORY_START_DATE: LocalDate = LocalDate.of(EPOCH_YEAR, 1, 1)

private data class ReferenceData(
    val categories: List<Category>,
    val brands: List<Brand>,
    val occasions: List<Occasion>,
    val tags: List<Tag>,
)

@HiltViewModel
class OutfitDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val outfitRepository: OutfitRepository,
        garmentRepository: GarmentRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        occasionRepository: OccasionRepository,
        tagRepository: TagRepository,
        wearEventRepository: WearEventRepository,
        wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
    ) : ViewModel() {
        private val outfitId = OutfitId(savedStateHandle.toRoute<OutfitDetailRoute>().outfitId)
        private val deleteBlockedMessageFlow = MutableStateFlow<String?>(null)
        val deleteBlockedMessage: StateFlow<String?> = deleteBlockedMessageFlow.asStateFlow()

        private val referenceDataFlow =
            combine(
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                occasionRepository.observeAll(),
                tagRepository.observeAll(),
            ) { categories, brands, occasions, tags -> ReferenceData(categories, brands, occasions, tags) }

        val uiState: StateFlow<OutfitDetailUiState> =
            combine(
                outfitRepository.observeOutfit(outfitId),
                garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
                referenceDataFlow,
                wearEventRepository.observeEvents(DateRange(HISTORY_START_DATE, LocalDate.now())),
                wardrobeIntelligenceRepository.observeOutfitInsights(outfitId),
            ) { outfit, garments, reference, wearEvents, insights ->
                if (outfit == null) {
                    OutfitDetailUiState(isLoading = false, notFound = true)
                } else {
                    buildUiState(outfit, garments, reference, wearEvents, insights)
                }
            }.catch { throwable ->
                emit(OutfitDetailUiState(isLoading = false, error = throwable.message ?: "Something went wrong."))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = OutfitDetailUiState(isLoading = true),
            )

        private fun buildUiState(
            outfit: Outfit,
            garments: List<Garment>,
            reference: ReferenceData,
            wearEvents: List<WearEvent>,
            insights: OutfitInsights?,
        ): OutfitDetailUiState {
            val categoriesById = reference.categories.associateBy { it.id }
            val brandsById = reference.brands.associateBy { it.id }
            val garmentsById = garments.associateBy { it.id }

            val slots =
                outfit.garments
                    .sortedBy { it.layerSlot }
                    .mapNotNull { slot ->
                        val garment = garmentsById[slot.garmentId] ?: return@mapNotNull null
                        OutfitSlotUiModel(
                            slot = OutfitSlot.fromIndex(slot.layerSlot),
                            garment = garment.toTileUiModel(categoriesById, brandsById),
                        )
                    }

            val ownWears =
                wearEvents
                    .filter { it.outfitId == outfit.id && it.status == WearEventStatus.WORN }
                    .sortedByDescending { it.date }

            return OutfitDetailUiState(
                isLoading = false,
                title = outfit.name?.takeUnless { it.isBlank() } ?: "Untitled look",
                isFavorite = outfit.isFavorite,
                isArchived = outfit.isArchived,
                notes = outfit.notes,
                mood = outfit.mood,
                occasionName = outfit.occasionId?.let { occ -> reference.occasions.firstOrNull { it.id == occ }?.name },
                seasonNames = outfit.seasons.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                dressCodeNames =
                    outfit.dressCodes.map {
                        it.name
                            .lowercase()
                            .replace('_', ' ')
                            .replaceFirstChar(Char::uppercase)
                    },
                tagNames = outfit.tagIds.mapNotNull { tagId -> reference.tags.firstOrNull { it.id == tagId }?.name },
                slots = slots,
                wearCount = ownWears.size,
                lastWornDate = ownWears.firstOrNull()?.date,
                wearHistory = ownWears.map { it.date },
                insights = insights,
            )
        }

        fun onToggleFavorite() {
            viewModelScope.launch { outfitRepository.setFavorite(outfitId, !uiState.value.isFavorite) }
        }

        fun onArchive(isArchived: Boolean) {
            viewModelScope.launch { outfitRepository.setArchived(outfitId, isArchived) }
        }

        fun onDuplicate(onDuplicated: (Long) -> Unit) {
            viewModelScope.launch {
                val source = outfitRepository.getOutfit(outfitId) ?: return@launch
                val newId =
                    outfitRepository.saveOutfit(
                        source.copy(
                            id = OutfitId(0),
                            name = source.name?.let { "$it copy" } ?: "Untitled look copy",
                            isFavorite = false,
                            isArchived = false,
                            createdAt = Instant.now(),
                        ),
                    )
                onDuplicated(newId.value)
            }
        }

        fun onDelete(onDeleted: () -> Unit) {
            viewModelScope.launch {
                runCatching { outfitRepository.deleteOutfit(outfitId) }
                    .onSuccess { onDeleted() }
                    .onFailure {
                        deleteBlockedMessageFlow.value =
                            "This look has wear history and can't be deleted. Try archiving it instead."
                    }
            }
        }

        fun onDeleteBlockedMessageShown() {
            deleteBlockedMessageFlow.value = null
        }
    }
