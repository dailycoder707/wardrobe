package com.wardrobe.app.feature.outfits.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitSort
import com.wardrobe.app.core.model.outfit.OutfitSortField
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L
private const val STOP_TIMEOUT_MS = 5000L
private const val MAX_CARD_THUMBNAILS = 4
private const val EPOCH_YEAR = 1970
private val HISTORY_START_DATE: LocalDate = LocalDate.of(EPOCH_YEAR, 1, 1)

private data class WearStats(
    val countByOutfitId: Map<Long, Int>,
    val lastWornByOutfitId: Map<Long, LocalDate>,
)

private data class UiInputs(
    val filters: SavedLooksFilterState,
    val query: String,
    val sort: OutfitSort,
    val toast: String?,
    val totalUnfilteredCount: Int,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedLooksViewModel
    @Inject
    constructor(
        private val outfitRepository: OutfitRepository,
        garmentRepository: GarmentRepository,
        wearEventRepository: WearEventRepository,
        occasionRepository: OccasionRepository,
    ) : ViewModel() {
        private val filterState = MutableStateFlow(SavedLooksFilterState.EMPTY)
        private val searchQueryRaw = MutableStateFlow("")
        private val sortState = MutableStateFlow(OutfitSort.DEFAULT)
        private val toastMessage = MutableStateFlow<String?>(null)

        private val searchQueryDebounced = searchQueryRaw.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

        private val sqlFilterFlow =
            combine(filterState, searchQueryDebounced) { filters, query ->
                OutfitFilter(
                    occasionId = filters.occasionId,
                    isSaved = true,
                    isArchived = filters.showArchived,
                    isFavorite = true.takeIf { filters.favoriteOnly },
                    searchQuery = query.takeIf { it.isNotBlank() },
                )
            }.distinctUntilChanged()

        private val outfitsAndGarmentsFlow =
            combine(
                sqlFilterFlow.flatMapLatest { outfitRepository.observeOutfits(it) },
                garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
            ) { outfits, garments -> outfits to garments }

        private val wearEventsAndOccasionsFlow =
            combine(
                wearEventRepository.observeEvents(DateRange(HISTORY_START_DATE, LocalDate.now())),
                occasionRepository.observeAll(),
            ) { events, occasions -> events to occasions }

        private val uiInputsFlow =
            combine(
                filterState,
                searchQueryRaw,
                sortState,
                toastMessage,
                outfitRepository.observeOutfits(OutfitFilter(isSaved = true, isArchived = false)),
            ) { filters, query, sort, toast, unfiltered ->
                UiInputs(filters, query, sort, toast, unfiltered.size)
            }

        val uiState: StateFlow<SavedLooksUiState> =
            combine(outfitsAndGarmentsFlow, wearEventsAndOccasionsFlow, uiInputsFlow) {
                outfitsAndGarments,
                wearEventsAndOccasions,
                inputs,
                ->
                buildUiState(outfitsAndGarments, wearEventsAndOccasions, inputs)
            }.catch { throwable ->
                emit(SavedLooksUiState(isLoading = false, toastMessage = throwable.message))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SavedLooksUiState(isLoading = true),
            )

        private fun buildUiState(
            outfitsAndGarments: Pair<List<Outfit>, List<Garment>>,
            wearEventsAndOccasions: Pair<List<WearEvent>, List<Occasion>>,
            inputs: UiInputs,
        ): SavedLooksUiState {
            val (outfits, garments) = outfitsAndGarments
            val (wearEvents, occasions) = wearEventsAndOccasions
            val garmentsById = garments.associateBy { it.id }
            val wearStats = buildWearStats(wearEvents)
            val sorted = sortOutfits(outfits, wearStats, inputs.sort)

            return SavedLooksUiState(
                isLoading = false,
                outfits = sorted.map { it.toCardUiModel(garmentsById, occasions, wearStats) },
                totalUnfilteredCount = inputs.totalUnfilteredCount,
                searchQuery = inputs.query,
                filters = inputs.filters,
                occasionOptions = occasions,
                sort = inputs.sort,
                toastMessage = inputs.toast,
            )
        }

        fun onSearchQueryChange(query: String) {
            searchQueryRaw.value = query
        }

        fun onFiltersChange(filters: SavedLooksFilterState) {
            filterState.value = filters
        }

        fun onClearFilters() {
            filterState.value = SavedLooksFilterState.EMPTY
        }

        fun onSortChange(sort: OutfitSort) {
            sortState.value = sort
        }

        fun onToggleFavorite(
            id: Long,
            isFavorite: Boolean,
        ) {
            viewModelScope.launch { outfitRepository.setFavorite(OutfitId(id), isFavorite) }
        }

        fun onArchive(
            id: Long,
            isArchived: Boolean,
        ) {
            viewModelScope.launch {
                outfitRepository.setArchived(OutfitId(id), isArchived)
                toastMessage.value = if (isArchived) "Look archived" else "Look restored"
            }
        }

        fun onDuplicate(id: Long) {
            viewModelScope.launch {
                val source = outfitRepository.getOutfit(OutfitId(id)) ?: return@launch
                outfitRepository.saveOutfit(
                    source.copy(
                        id = OutfitId(0),
                        name = source.name?.let { "$it copy" } ?: "Untitled look copy",
                        isFavorite = false,
                        isArchived = false,
                        createdAt = Instant.now(),
                    ),
                )
                toastMessage.value = "Look duplicated"
            }
        }

        fun onDelete(id: Long) {
            viewModelScope.launch {
                runCatching { outfitRepository.deleteOutfit(OutfitId(id)) }
                    .onSuccess { toastMessage.value = "Look deleted" }
                    .onFailure { toastMessage.value = "Can't delete — this look has wear history." }
            }
        }

        fun onToastShown() {
            toastMessage.value = null
        }
    }

private fun buildWearStats(events: List<WearEvent>): WearStats {
    val worn = events.filter { it.outfitId != null && it.status == WearEventStatus.WORN }
    val countByOutfitId = worn.groupingBy { it.outfitId!!.value }.eachCount()
    val lastWornByOutfitId = worn.groupBy { it.outfitId!!.value }.mapValues { (_, wears) -> wears.maxOf { it.date } }
    return WearStats(countByOutfitId, lastWornByOutfitId)
}

private fun sortOutfits(
    outfits: List<Outfit>,
    wearStats: WearStats,
    sort: OutfitSort,
): List<Outfit> {
    val comparator: Comparator<Outfit> =
        when (sort.field) {
            OutfitSortField.RECENTLY_ADDED -> {
                compareBy { it.createdAt }
            }

            OutfitSortField.RECENTLY_WORN -> {
                compareBy(nullsFirst<LocalDate>()) { wearStats.lastWornByOutfitId[it.id.value] }
            }

            OutfitSortField.MOST_WORN -> {
                compareBy { wearStats.countByOutfitId[it.id.value] ?: 0 }
            }

            OutfitSortField.ALPHABETICAL -> {
                compareBy { it.name.orEmpty().lowercase() }
            }
        }
    val ordered = outfits.sortedWith(comparator)
    return if (sort.direction == SortDirection.DESCENDING) ordered.reversed() else ordered
}

private fun Outfit.toCardUiModel(
    garmentsById: Map<GarmentId, Garment>,
    occasions: List<Occasion>,
    wearStats: WearStats,
): OutfitCardUiModel {
    val thumbnails =
        garments
            .sortedBy { it.layerSlot }
            .mapNotNull { slot -> garmentsById[slot.garmentId] }
            .mapNotNull { garment -> garment.images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath }
            .take(MAX_CARD_THUMBNAILS)
    return OutfitCardUiModel(
        id = id.value,
        title = name?.takeUnless { it.isBlank() } ?: "Untitled look",
        thumbnailPaths = thumbnails,
        isFavorite = isFavorite,
        occasionName = occasionId?.let { occ -> occasions.firstOrNull { it.id == occ }?.name },
        wearCount = wearStats.countByOutfitId[id.value] ?: 0,
    )
}
