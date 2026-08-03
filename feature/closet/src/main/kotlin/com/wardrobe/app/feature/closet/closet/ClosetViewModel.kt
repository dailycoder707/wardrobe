package com.wardrobe.app.feature.closet.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ClosetPreferencesRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentSortField
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.feature.closet.common.toTileUiModel
import com.wardrobe.app.feature.closet.debug.ClosetDiagnostics
import com.wardrobe.app.feature.closet.debug.ClosetDiagnosticsSnapshot
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L
private const val STOP_TIMEOUT_MS = 5000L
private const val RECENTLY_WORN_DAYS = 30L
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6

private data class ReferenceData(
    val categories: List<Category>,
    val colors: List<Color>,
    val brands: List<Brand>,
    val materials: List<Material>,
    val tags: List<Tag>,
)

private data class GarmentsWithStats(
    val garments: List<Garment>,
    val costPerWear: List<CostPerWearEntry>,
)

private data class MiscState(
    val selection: ClosetSelectionState,
    val toast: String?,
    val totalUnfilteredCount: Int,
    val recentSearches: List<String>,
)

private data class UiInputs(
    val sort: GarmentSort,
    val gridColumnCount: Int,
    val filters: ClosetFilterState,
    val rawQuery: String,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClosetViewModel
    @Inject
    constructor(
        private val garmentRepository: GarmentRepository,
        private val statsRepository: StatsRepository,
        private val closetPreferencesRepository: ClosetPreferencesRepository,
        private val closetDiagnostics: ClosetDiagnostics,
        categoryRepository: CategoryRepository,
        colorRepository: ColorRepository,
        brandRepository: BrandRepository,
        materialRepository: MaterialRepository,
        tagRepository: TagRepository,
    ) : ViewModel() {
        private val filterState = MutableStateFlow(ClosetFilterState.EMPTY)
        private val searchQueryRaw = MutableStateFlow("")
        private val toastMessage = MutableStateFlow<String?>(null)

        val selection = ClosetSelectionController(garmentRepository, viewModelScope) { toastMessage.value = it }

        private val searchQueryDebounced = searchQueryRaw.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

        private val sqlFilterFlow =
            combine(filterState, searchQueryDebounced) { filters, query ->
                GarmentFilter(
                    categoryId = filters.category,
                    brandId = filters.brand,
                    status = GarmentStatus.ACTIVE,
                    season = filters.season,
                    dressCode = filters.dressCode,
                    searchQuery = query.takeIf { it.isNotBlank() },
                    isFavorite = true.takeIf { filters.favoriteOnly },
                    colorId = filters.color,
                    materialId = filters.material,
                    tagId = filters.tag,
                    priceMin = filters.priceMin,
                    priceMax = filters.priceMax,
                )
            }.distinctUntilChanged()

        private val garmentsWithStatsFlow =
            combine(
                sqlFilterFlow.flatMapLatest { garmentRepository.observeGarments(it) },
                statsRepository.observeCostPerWear(),
            ) { garments, costPerWear -> GarmentsWithStats(garments, costPerWear) }

        private val referenceDataFlow =
            combine(
                categoryRepository.observeAll(),
                colorRepository.observeAll(),
                brandRepository.observeAll(),
                materialRepository.observeAll(),
                tagRepository.observeAll(),
            ) { categories, colors, brands, materials, tags ->
                ReferenceData(categories, colors, brands, materials, tags)
            }

        private val uiInputsFlow =
            combine(
                closetPreferencesRepository.observeSort(),
                closetPreferencesRepository.observeGridColumnCount(),
                filterState,
                searchQueryRaw,
            ) { sort, gridColumnCount, filters, rawQuery -> UiInputs(sort, gridColumnCount, filters, rawQuery) }

        private val totalUnfilteredCountFlow =
            garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)).map { it.size }

        val uiState: StateFlow<ClosetUiState> =
            combine(
                garmentsWithStatsFlow,
                referenceDataFlow,
                uiInputsFlow,
                combine(
                    selection.state,
                    toastMessage,
                    totalUnfilteredCountFlow,
                    closetPreferencesRepository.observeRecentSearches(),
                ) { selectionState, toast, total, recentSearches ->
                    MiscState(selectionState, toast, total, recentSearches)
                },
            ) { garmentsWithStats, reference, uiInputs, misc ->
                buildUiState(garmentsWithStats, reference, uiInputs, misc, closetDiagnostics)
            }.catch { throwable ->
                emit(ClosetUiState(isLoading = false, error = throwable.message ?: "Something went wrong."))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ClosetUiState(isLoading = true),
            )

        fun onSearchQueryChange(query: String) {
            searchQueryRaw.value = query
        }

        fun onSearchSubmit(query: String) {
            if (query.isBlank()) return
            viewModelScope.launch { closetPreferencesRepository.addRecentSearch(query) }
        }

        fun onClearSearchHistory() {
            viewModelScope.launch { closetPreferencesRepository.clearRecentSearches() }
        }

        fun onFiltersChange(filters: ClosetFilterState) {
            filterState.value = filters
        }

        fun onClearFilters() {
            filterState.value = ClosetFilterState.EMPTY
        }

        fun onSortChange(sort: GarmentSort) {
            viewModelScope.launch { closetPreferencesRepository.setSort(sort) }
        }

        fun onGridColumnCountChange(count: Int) {
            viewModelScope.launch {
                closetPreferencesRepository.setGridColumnCount(
                    count.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
                )
            }
        }

        fun onToggleFavorite(garmentId: Long) {
            viewModelScope.launch {
                val current = uiState.value.garments.firstOrNull { it.id == garmentId } ?: return@launch
                garmentRepository.setFavorite(GarmentId(garmentId), !current.isFavorite)
            }
        }

        fun onToastShown() {
            toastMessage.value = null
        }
    }

private fun buildUiState(
    garmentsWithStats: GarmentsWithStats,
    reference: ReferenceData,
    inputs: UiInputs,
    misc: MiscState,
    closetDiagnostics: ClosetDiagnostics,
): ClosetUiState {
    val categoriesById = reference.categories.associateBy { it.id }
    val brandsById = reference.brands.associateBy { it.id }
    val statsByGarmentId = garmentsWithStats.costPerWear.associateBy { it.garmentId }
    val today = LocalDate.now()

    var garments = garmentsWithStats.garments
    if (misc.selection.pendingDeletionIds.isNotEmpty()) {
        garments = garments.filterNot { it.id.value in misc.selection.pendingDeletionIds }
    }
    if (inputs.filters.neverWorn) {
        garments = garments.filter { (statsByGarmentId[it.id]?.totalWearCount ?: 0) == 0 }
    }
    if (inputs.filters.recentlyWornOnly) {
        garments =
            garments.filter { garment ->
                val lastWorn = statsByGarmentId[garment.id]?.lastWornDate
                lastWorn != null && lastWorn.isAfter(today.minusDays(RECENTLY_WORN_DAYS))
            }
    }

    val sorted = sortGarments(garments, statsByGarmentId, brandsById, inputs.sort)

    closetDiagnostics.report(
        ClosetDiagnosticsSnapshot(
            searchQuery = inputs.rawQuery,
            activeFilterCount = inputs.filters.activeCount,
            filterSummary = inputs.filters.toString(),
            sortSummary = "${inputs.sort.field} ${inputs.sort.direction}",
            resultCount = sorted.size,
        ),
    )

    return ClosetUiState(
        isLoading = false,
        garments = sorted.map { it.toTileUiModel(categoriesById, brandsById) },
        totalUnfilteredCount = misc.totalUnfilteredCount,
        searchQuery = inputs.rawQuery,
        filters = inputs.filters,
        filterOptions =
            ClosetFilterOptions(
                reference.categories,
                reference.colors,
                reference.brands,
                reference.materials,
                reference.tags,
            ),
        sort = inputs.sort,
        gridColumnCount = inputs.gridColumnCount,
        isSelectionMode = misc.selection.isSelectionMode,
        selectedIds = misc.selection.selectedIds,
        recentSearches = misc.recentSearches,
        toastMessage = misc.toast,
    )
}

private fun sortGarments(
    garments: List<Garment>,
    statsByGarmentId: Map<GarmentId, CostPerWearEntry>,
    brandsById: Map<BrandId, Brand>,
    sort: GarmentSort,
): List<Garment> {
    val comparator: Comparator<Garment> =
        when (sort.field) {
            GarmentSortField.RECENTLY_ADDED -> {
                compareBy { garment -> garment.createdAt }
            }

            GarmentSortField.RECENTLY_WORN -> {
                compareBy(nullsFirst<LocalDate>()) { garment -> statsByGarmentId[garment.id]?.lastWornDate }
            }

            GarmentSortField.ALPHABETICAL -> {
                compareBy { garment -> garment.name.orEmpty().lowercase() }
            }

            GarmentSortField.BRAND -> {
                compareBy { garment -> brandsById[garment.brandId]?.name?.lowercase().orEmpty() }
            }

            GarmentSortField.COLOR -> {
                compareBy { garment ->
                    garment.palette
                        .firstOrNull()
                        ?.color
                        ?.name
                        ?.lowercase()
                        .orEmpty()
                }
            }

            GarmentSortField.PRICE -> {
                compareBy(nullsFirst<Double>()) { garment -> garment.price?.amount }
            }

            GarmentSortField.WEAR_COUNT -> {
                compareBy { garment -> statsByGarmentId[garment.id]?.totalWearCount ?: 0 }
            }

            GarmentSortField.COST_PER_WEAR -> {
                compareBy(nullsFirst<Double>()) { garment -> statsByGarmentId[garment.id]?.costPerWear }
            }
        }
    val ordered = garments.sortedWith(comparator)
    return if (sort.direction == SortDirection.DESCENDING) ordered.reversed() else ordered
}
