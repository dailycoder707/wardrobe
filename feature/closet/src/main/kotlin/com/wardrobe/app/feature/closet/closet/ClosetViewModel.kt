package com.wardrobe.app.feature.closet.closet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ClosetPreferencesRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentSortField
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
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
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6

private data class ReferenceData(
    val categories: List<Category>,
    val colors: List<Color>,
    val brands: List<Brand>,
    val materials: List<Material>,
    val fabrics: List<Fabric>,
    val tags: List<Tag>,
    val occasions: List<Occasion>,
)

/** Split purely to stay under `kotlinx.coroutines.flow.combine`'s 5-flow
 * overload limit — combined back into one [ReferenceData] immediately. */
private data class ReferenceDataCore(
    val categories: List<Category>,
    val colors: List<Color>,
    val brands: List<Brand>,
    val materials: List<Material>,
)

private data class ReferenceDataExtra(
    val fabrics: List<Fabric>,
    val tags: List<Tag>,
    val occasions: List<Occasion>,
)

private data class GarmentsWithStats(
    val garments: List<Garment>,
    val costPerWear: List<CostPerWearEntry>,
)

private data class WardrobeSummary(
    val totalCount: Int,
    val insights: List<ClosetInsight>,
)

private data class MiscState(
    val selection: ClosetSelectionState,
    val toast: String?,
    val summary: WardrobeSummary,
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
    @Suppress("LongParameterList")
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
        fabricRepository: FabricRepository,
        tagRepository: TagRepository,
        occasionRepository: OccasionRepository,
    ) : ViewModel() {
        private val filterState = MutableStateFlow(ClosetFilterState.EMPTY)
        private val searchQueryRaw = MutableStateFlow("")
        private val toastMessage = MutableStateFlow<String?>(null)

        val selection = ClosetSelectionController(garmentRepository, viewModelScope) { toastMessage.value = it }

        private val searchQueryDebounced = searchQueryRaw.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

        /** Only search + status are pushed down to SQL now — every facet
         * (category/color/brand/material/fabric/season/dressCode/occasion/
         * tag/fit/gender/waterproofLevel/price/wear-history) is evaluated
         * in-memory via [matchesClosetFilters] in [buildUiState] instead, so
         * toggling a filter never re-queries the database (M17 architecture
         * decision, ADR-016) — it only recomputes the already-loaded list. */
        private val sqlFilterFlow =
            searchQueryDebounced
                .map { query ->
                    GarmentFilter(status = GarmentStatus.ACTIVE, searchQuery = query.takeIf { it.isNotBlank() })
                }.distinctUntilChanged()

        private val garmentsWithStatsFlow =
            combine(
                sqlFilterFlow.flatMapLatest { garmentRepository.observeGarments(it) },
                statsRepository.observeCostPerWear(),
            ) { garments, costPerWear -> GarmentsWithStats(garments, costPerWear) }

        private val referenceDataFlow =
            combine(
                combine(
                    categoryRepository.observeAll(),
                    colorRepository.observeAll(),
                    brandRepository.observeAll(),
                    materialRepository.observeAll(),
                ) { categories, colors, brands, materials -> ReferenceDataCore(categories, colors, brands, materials) },
                combine(
                    fabricRepository.observeAll(),
                    tagRepository.observeAll(),
                    occasionRepository.observeAll(),
                ) { fabrics, tags, occasions -> ReferenceDataExtra(fabrics, tags, occasions) },
            ) { core, extra ->
                ReferenceData(
                    categories = core.categories,
                    colors = core.colors,
                    brands = core.brands,
                    materials = core.materials,
                    fabrics = extra.fabrics,
                    tags = extra.tags,
                    occasions = extra.occasions,
                )
            }

        private val uiInputsFlow =
            combine(
                closetPreferencesRepository.observeSort(),
                closetPreferencesRepository.observeGridColumnCount(),
                filterState,
                searchQueryRaw,
            ) { sort, gridColumnCount, filters, rawQuery -> UiInputs(sort, gridColumnCount, filters, rawQuery) }

        /** Every active garment regardless of search/filters — the source
         * both of [ClosetUiState.resultCountLabel]'s "of N" total and of
         * [ClosetInsight]s, both of which must describe the whole wardrobe,
         * not the current narrowed view. */
        private val wardrobeSummaryFlow =
            garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)).map { garments ->
                WardrobeSummary(totalCount = garments.size, insights = computeClosetInsights(garments, LocalDate.now()))
            }

        val uiState: StateFlow<ClosetUiState> =
            combine(
                garmentsWithStatsFlow,
                referenceDataFlow,
                uiInputsFlow,
                combine(
                    selection.state,
                    toastMessage,
                    wardrobeSummaryFlow,
                    closetPreferencesRepository.observeRecentSearches(),
                ) { selectionState, toast, summary, recentSearches ->
                    MiscState(selectionState, toast, summary, recentSearches)
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
    garments =
        garments.filter { garment ->
            garment.matchesClosetFilters(inputs.filters, categoriesById, statsByGarmentId[garment.id], today)
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
        totalUnfilteredCount = misc.summary.totalCount,
        searchQuery = inputs.rawQuery,
        filters = inputs.filters,
        filterOptions =
            ClosetFilterOptions(
                categories = reference.categories,
                colors = reference.colors,
                brands = reference.brands,
                materials = reference.materials,
                fabrics = reference.fabrics,
                tags = reference.tags,
                occasions = reference.occasions,
            ),
        sort = inputs.sort,
        gridColumnCount = inputs.gridColumnCount,
        isSelectionMode = misc.selection.isSelectionMode,
        selectedIds = misc.selection.selectedIds,
        recentSearches = misc.recentSearches,
        toastMessage = misc.toast,
        insights = misc.summary.insights,
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

            GarmentSortField.FAVORITE_FIRST -> {
                compareBy { garment -> garment.isFavorite }
            }
        }
    val ordered = garments.sortedWith(comparator)
    return if (sort.direction == SortDirection.DESCENDING) ordered.reversed() else ordered
}
