package com.wardrobe.app.feature.stats.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.stats.CategoryWearCountEntry
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WardrobeMixDistribution
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.ui.debug.StatsDiagnostics
import com.wardrobe.app.feature.stats.common.charts.HeatmapCellUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Groups the three taxonomy-reference repositories [InsightsViewModel]
 * needs only for M21's material/fabric/occasion distribution charts, so its
 * own constructor stays under detekt's `LongParameterList` threshold — the
 * same "bag of repositories" pattern `StylingContextDependencies` (core:data)
 * already established. */
class TaxonomyDistributionRepositories
    @Inject
    constructor(
        val materialRepository: MaterialRepository,
        val fabricRepository: FabricRepository,
        val occasionRepository: OccasionRepository,
    )

private const val STOP_TIMEOUT_MS = 5000L
private const val HEATMAP_WEEKS = 18L
private const val RECENT_ACTIVITY_DAYS = 14L

internal data class ReferenceData(
    val garments: List<Garment>,
    val outfits: List<Outfit>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
    val colorsById: Map<ColorId, Color>,
    val materialsById: Map<MaterialId, Material>,
    val fabricsById: Map<FabricId, Fabric>,
    val occasionsById: Map<OccasionId, Occasion>,
)

private data class PartialReferenceData(
    val garments: List<Garment>,
    val outfits: List<Outfit>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
    val colorsById: Map<ColorId, Color>,
)

private data class TaxonomyReferenceData(
    val materialsById: Map<MaterialId, Material>,
    val fabricsById: Map<FabricId, Fabric>,
    val occasionsById: Map<OccasionId, Occasion>,
)

internal data class InsightsListsSource(
    val costPerWear: List<CostPerWearEntry>,
    val dormant: List<DormantItem>,
    val missingOutfits: List<GarmentId>,
    val neverWornOutfits: List<OutfitId>,
    val repeatedOutfits: List<RepeatedOutfit>,
)

private data class ListsAndDistribution(
    val lists: InsightsListsSource,
    val distribution: WardrobeMixDistribution,
)

private data class InsightsSourceData(
    val usage: UsageStats,
    val heatmap: List<WearHeatmapDay>,
    val lists: InsightsListsSource,
    val activity: List<WearEvent>,
    val distribution: WardrobeMixDistribution,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        garmentRepository: GarmentRepository,
        outfitRepository: OutfitRepository,
        categoryRepository: CategoryRepository,
        brandRepository: BrandRepository,
        colorRepository: ColorRepository,
        wearEventRepository: WearEventRepository,
        taxonomyRepositories: TaxonomyDistributionRepositories,
        clock: Clock,
    ) : ViewModel() {
        private val windowState = MutableStateFlow(StatsWindow.ONE_YEAR)
        private val today = LocalDate.now(clock)
        private val heatmapRange = DateRange(today.minusWeeks(HEATMAP_WEEKS), today)
        private val activityRange = DateRange(today.minusDays(RECENT_ACTIVITY_DAYS), today)

        private val partialReferenceFlow =
            combine(
                garmentRepository.observeGarments(GarmentFilter()),
                outfitRepository.observeOutfits(OutfitFilter()),
                categoryRepository.observeAll(),
                brandRepository.observeAll(),
                colorRepository.observeAll(),
            ) { garments, outfits, categories, brands, colors ->
                PartialReferenceData(
                    garments = garments,
                    outfits = outfits,
                    categoriesById = categories.associateBy { it.id },
                    brandsById = brands.associateBy { it.id },
                    colorsById = colors.associateBy { it.id },
                )
            }

        private val taxonomyReferenceFlow =
            combine(
                taxonomyRepositories.materialRepository.observeAll(),
                taxonomyRepositories.fabricRepository.observeAll(),
                taxonomyRepositories.occasionRepository.observeAll(),
            ) { materials, fabrics, occasions ->
                TaxonomyReferenceData(
                    materialsById = materials.associateBy { it.id },
                    fabricsById = fabrics.associateBy { it.id },
                    occasionsById = occasions.associateBy { it.id },
                )
            }

        private val referenceDataFlow =
            combine(partialReferenceFlow, taxonomyReferenceFlow) { partial, taxonomy ->
                ReferenceData(
                    garments = partial.garments,
                    outfits = partial.outfits,
                    categoriesById = partial.categoriesById,
                    brandsById = partial.brandsById,
                    colorsById = partial.colorsById,
                    materialsById = taxonomy.materialsById,
                    fabricsById = taxonomy.fabricsById,
                    occasionsById = taxonomy.occasionsById,
                )
            }

        private val usageStatsFlow = windowState.flatMapLatest { statsRepository.observeUsageStats(it) }

        private val heatmapFlow = statsRepository.observeWearHeatmap(heatmapRange)

        /** M21 bug fix: this used to hardcode [StatsWindow.ALL_TIME] regardless
         * of [window], even though it's built inside `windowState.flatMapLatest { window -> ... }`
         * with `window` right there unused — so "Waiting to Be Worn" never
         * actually respected the period selector while every other section
         * did. Part 2's own requirement ("the selected period must
         * consistently affect every statistic that is supposed to be
         * period-dependent") is exactly what this violated. */
        private val listsFlow =
            windowState.flatMapLatest { window ->
                combine(
                    statsRepository.observeCostPerWear(),
                    statsRepository.observeDormantItems(window),
                    statsRepository.observeWardrobeUsageGaps(),
                    statsRepository.observeRepeatedOutfits(window),
                ) { costPerWear, dormant, usageGaps, repeated ->
                    InsightsListsSource(
                        costPerWear,
                        dormant,
                        usageGaps.garmentsMissingOutfits,
                        usageGaps.neverWornOutfitIds,
                        repeated,
                    )
                }
            }

        /** M21 — composition-based, so deliberately not re-queried on window
         * change (owning a material doesn't stop being true outside the
         * selected period). */
        private val distributionFlow = statsRepository.observeWardrobeMixDistribution()

        private val listsAndDistributionFlow =
            combine(listsFlow, distributionFlow) { lists, distribution -> ListsAndDistribution(lists, distribution) }

        private val activityFlow = wearEventRepository.observeEvents(activityRange)

        val uiState: StateFlow<InsightsUiState> =
            combine(
                referenceDataFlow,
                usageStatsFlow,
                heatmapFlow,
                listsAndDistributionFlow,
                activityFlow,
            ) { ref, usage, heatmap, listsAndDistribution, activity ->
                val source =
                    InsightsSourceData(
                        usage = usage,
                        heatmap = heatmap,
                        lists = listsAndDistribution.lists,
                        activity = activity,
                        distribution = listsAndDistribution.distribution,
                    )
                buildInsightsUiState(ref, source, today, heatmapRange)
            }.onStart { StatsDiagnostics.onSubscribed() }
                .onCompletion { StatsDiagnostics.onUnsubscribed() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = InsightsUiState(),
                )

        fun onWindowChange(window: StatsWindow) {
            windowState.value = window
        }
    }

private fun buildInsightsUiState(
    ref: ReferenceData,
    source: InsightsSourceData,
    today: LocalDate,
    heatmapRange: DateRange,
): InsightsUiState {
    val usage = source.usage
    val favoritesBySlot = favoriteCategoryNamesBySlot(usage.categoryWearCounts, ref.categoriesById)
    return InsightsUiState(
        isLoading = false,
        window = usage.window,
        usagePercent = usage.usagePercent.toInt(),
        totalActiveGarments = usage.totalActiveGarments,
        wornAtLeastOnce = usage.wornAtLeastOnce,
        favoriteBrandNames = usage.favouriteBrandIds.mapNotNull { ref.brandsById[it]?.name },
        favoriteColorNames = usage.signatureColorIds.mapNotNull { ref.colorsById[it]?.name },
        favoriteCategoryNames = usage.favouriteCategoryIds.mapNotNull { ref.categoriesById[it]?.name },
        charts = buildCharts(usage, source.heatmap, heatmapRange, source.distribution, ref),
        lists = buildLists(ref.garments, ref.outfits, source.lists, source.activity, today, usage),
        favoriteFootwearName = favoritesBySlot[OutfitSlot.SHOES],
        favoriteBagName = favoritesBySlot[OutfitSlot.BAG],
        favoriteJewelryName = favoritesBySlot[OutfitSlot.JEWELRY],
        favoriteAccessoryName = favoritesBySlot[OutfitSlot.ACCESSORIES],
        unusedWardrobePercent = (UNUSED_PERCENT_TOTAL - usage.usagePercent).toInt().coerceIn(0, UNUSED_PERCENT_TOTAL),
    )
}

private const val UNUSED_PERCENT_TOTAL = 100

private fun favoriteCategoryNamesBySlot(
    categoryWearCounts: List<CategoryWearCountEntry>,
    categoriesById: Map<CategoryId, Category>,
): Map<OutfitSlot, String> =
    categoryWearCounts
        .mapNotNull { entry ->
            val category = categoriesById[entry.categoryId] ?: return@mapNotNull null
            val slot = OutfitSlot.classify(category.name) ?: return@mapNotNull null
            Triple(slot, category.name, entry.wearCount)
        }.groupBy { it.first }
        .mapValues { (_, entries) -> entries.maxBy { it.third } }
        .mapValues { (_, best) -> best.second }
