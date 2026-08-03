package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.StatsDao
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.stats.CategoryWearCountEntry
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.GarmentCombinationEntry
import com.wardrobe.app.core.model.stats.GarmentVersatilityEntry
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TOP_N = 5

/** [clock] is constructor-injected (defaulting to the system clock via Hilt's own
 * `@Provides`, not hand-wired here) specifically so window-boundary logic is
 * unit-testable against a fixed date rather than the real one — see
 * phase-5a-data-layer.md's testing strategy. */
class StatsRepositoryImpl
    @Inject
    constructor(
        private val dao: StatsDao,
        private val clock: Clock,
    ) : StatsRepository {
        override fun observeCostPerWear(): Flow<List<CostPerWearEntry>> =
            dao.observeCostPerWear().map { rows ->
                rows.map { row ->
                    CostPerWearEntry(
                        garmentId = GarmentId(row.garmentId),
                        totalWearCount = row.wearCount,
                        costPerWear = row.price?.takeIf { row.wearCount > 0 }?.div(row.wearCount),
                        lastWornDate = row.lastWornDate?.let(LocalDate::parse),
                    )
                }
            }

        override fun observeDormantItems(window: StatsWindow): Flow<List<DormantItem>> {
            val sinceDate = windowStartDate(window, clock)
            return dao.observeDormantSince(sinceDate.toString()).map { rows ->
                rows.map { row -> DormantItem(GarmentId(row.garmentId), row.lastWornDate?.let(LocalDate::parse)) }
            }
        }

        override fun observeUsageStats(window: StatsWindow): Flow<UsageStats> {
            val start = windowStartDate(window, clock).toString()
            val end = LocalDate.now(clock).toString()

            val coreCounts =
                combine(
                    dao.observeTotalActiveGarmentCount(),
                    dao.observeGarmentsWornInRange(start, end),
                    dao.observeCostPerWear(),
                ) { total, wornAtLeastOnce, costPerWear -> Triple(total, wornAtLeastOnce, costPerWear) }

            val partialBreakdowns =
                combine(
                    dao.observeWearCountBySeason(start, end),
                    dao.observeWearCountByDressCode(start, end),
                    dao.observeFavouriteBrands(start, end, TOP_N),
                    dao.observeSignatureColors(start, end, TOP_N),
                    dao.observeWeekdayVsWeekend(start, end),
                ) { bySeason, byDressCode, brands, colors, weekdayWeekend ->
                    PartialBreakdownRows(bySeason, byDressCode, brands, colors, weekdayWeekend)
                }

            val breakdowns =
                combine(
                    partialBreakdowns,
                    dao.observeFavouriteCategories(start, end, TOP_N),
                ) { partial, categories ->
                    BreakdownRows(
                        bySeason = partial.bySeason,
                        byDressCode = partial.byDressCode,
                        brands = partial.brands,
                        colors = partial.colors,
                        weekdayWeekend = partial.weekdayWeekend,
                        categories = categories,
                    )
                }

            val phase9Extras =
                combine(
                    dao.observeFavouriteCategories(start, end, Int.MAX_VALUE),
                    dao.observeOutfitAppearanceCountByGarment(),
                    dao.observeTopGarmentPairs(TOP_N),
                ) { allCategoryCounts, versatility, combinations ->
                    Phase9ExtraRows(allCategoryCounts, versatility, combinations)
                }

            return combine(coreCounts, breakdowns, phase9Extras) { (total, wornAtLeastOnce, costPerWear), b, extras ->
                buildUsageStats(window, total, wornAtLeastOnce, costPerWear, b, extras)
            }
        }

        /**
         * A modest, honestly-scoped gap check: a season or dress code with zero active
         * garments covering it at all. Richer multi-attribute heuristics (e.g. "no warm-
         * coloured formalwear," "nothing waterproof") need attributes this schema
         * doesn't track yet (pattern/color combinations, a waterproof flag) and are left
         * to Phase 6, not invented here — see phase-1-architecture.md Section 2's
         * `ClosetGap` description ("based on occasion/season coverage").
         */
        override fun observeClosetGaps(): Flow<List<ClosetGap>> =
            combine(
                dao.observeActiveGarmentCountBySeason(),
                dao.observeActiveGarmentCountByDressCode(),
            ) { seasonCounts, dressCodeCounts ->
                val seasonGaps =
                    seasonCounts.filter { it.garmentCount == 0 }.mapNotNull { row ->
                        runCatching { Season.valueOf(row.season) }.getOrNull()?.let { season ->
                            ClosetGap(
                                description = "Nothing in your closet is tagged for ${season.name.lowercase()} yet.",
                                relatedCategoryId = null,
                                relatedSeason = season,
                                relatedDressCode = null,
                            )
                        }
                    }
                val dressCodeGaps =
                    dressCodeCounts.filter { it.garmentCount == 0 }.mapNotNull { row ->
                        runCatching { DressCode.valueOf(row.dressCode) }.getOrNull()?.let { dressCode ->
                            ClosetGap(
                                description = "No ${dressCode.name.lowercase().replace('_', ' ')} items yet.",
                                relatedCategoryId = null,
                                relatedSeason = null,
                                relatedDressCode = dressCode,
                            )
                        }
                    }
                seasonGaps + dressCodeGaps
            }

        override fun observeWearHeatmap(range: DateRange): Flow<List<WearHeatmapDay>> =
            dao.observeWearCountByDate(range.start.toString(), range.end.toString()).map { rows ->
                rows.map { WearHeatmapDay(LocalDate.parse(it.date), it.wearCount) }
            }

        override fun observeRepeatedOutfits(
            window: StatsWindow,
            limit: Int,
        ): Flow<List<RepeatedOutfit>> {
            val start = windowStartDate(window, clock).toString()
            val end = LocalDate.now(clock).toString()
            return dao.observeOutfitWearCounts(start, end, limit).map { rows ->
                rows.map { RepeatedOutfit(OutfitId(it.outfitId), it.wearCount) }
            }
        }

        override fun observeNeverWornOutfitIds(): Flow<List<OutfitId>> =
            dao.observeNeverWornOutfitIds().map { ids -> ids.map(::OutfitId) }

        override fun observeGarmentsMissingOutfits(): Flow<List<GarmentId>> =
            dao.observeGarmentsMissingOutfitIds().map { ids -> ids.map(::GarmentId) }

        override fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int> {
            val start = windowStartDate(window, clock).toString()
            val end = LocalDate.now(clock).toString()
            return dao.observeOutfitWearEventCount(start, end)
        }

        override fun observeTopDressCode(
            window: StatsWindow,
            isWeekend: Boolean,
        ): Flow<DressCode?> {
            val start = windowStartDate(window, clock).toString()
            val end = LocalDate.now(clock).toString()
            return dao.observeDressCodeByDayType(start, end, isWeekend).map { rows ->
                rows.firstOrNull()?.let { row -> runCatching { DressCode.valueOf(row.dressCode) }.getOrNull() }
            }
        }
    }

private data class PartialBreakdownRows(
    val bySeason: List<com.wardrobe.app.core.database.dao.SeasonWearCountRow>,
    val byDressCode: List<com.wardrobe.app.core.database.dao.DressCodeWearCountRow>,
    val brands: List<com.wardrobe.app.core.database.dao.BrandWearCountRow>,
    val colors: List<com.wardrobe.app.core.database.dao.ColorWearCountRow>,
    val weekdayWeekend: List<com.wardrobe.app.core.database.dao.WeekdayWeekendRow>,
)

private data class BreakdownRows(
    val bySeason: List<com.wardrobe.app.core.database.dao.SeasonWearCountRow>,
    val byDressCode: List<com.wardrobe.app.core.database.dao.DressCodeWearCountRow>,
    val brands: List<com.wardrobe.app.core.database.dao.BrandWearCountRow>,
    val colors: List<com.wardrobe.app.core.database.dao.ColorWearCountRow>,
    val weekdayWeekend: List<com.wardrobe.app.core.database.dao.WeekdayWeekendRow>,
    val categories: List<com.wardrobe.app.core.database.dao.CategoryWearCountRow>,
)

/** Phase 9 — folded into [UsageStats] rather than three separate
 * [com.wardrobe.app.core.domain.repository.StatsRepository] methods (see that
 * type's Phase 9 additions doc comment for why). */
private data class Phase9ExtraRows(
    val allCategoryCounts: List<com.wardrobe.app.core.database.dao.CategoryWearCountRow>,
    val versatility: List<com.wardrobe.app.core.database.dao.GarmentOutfitCountRow>,
    val combinations: List<com.wardrobe.app.core.database.dao.GarmentPairRow>,
)

private const val SIX_MONTHS_COUNT = 6L

// LocalDate.EPOCH requires API 34; this project's minSdk is 26, so the epoch
// date is spelled out instead (see phase-5a-data-layer.md's minSdk record).
@Suppress("MagicNumber")
private val EPOCH_START_DATE: LocalDate = LocalDate.of(1970, 1, 1)

private fun windowStartDate(
    window: StatsWindow,
    clock: Clock,
): LocalDate {
    val today = LocalDate.now(clock)
    return when (window) {
        StatsWindow.ONE_MONTH -> today.minusMonths(1)
        StatsWindow.SIX_MONTHS -> today.minusMonths(SIX_MONTHS_COUNT)
        StatsWindow.ONE_YEAR -> today.minusYears(1)
        StatsWindow.ALL_TIME -> EPOCH_START_DATE
    }
}

private fun buildUsageStats(
    window: StatsWindow,
    total: Int,
    wornAtLeastOnce: Int,
    costPerWear: List<com.wardrobe.app.core.database.dao.CostPerWearRow>,
    breakdowns: BreakdownRows,
    extras: Phase9ExtraRows,
): UsageStats {
    val sortedByWear = costPerWear.sortedByDescending { it.wearCount }
    return UsageStats(
        window = window,
        totalActiveGarments = total,
        wornAtLeastOnce = wornAtLeastOnce,
        usagePercent = if (total > 0) (wornAtLeastOnce.toDouble() / total) * 100.0 else 0.0,
        mostWornGarmentIds = sortedByWear.take(TOP_N).map { GarmentId(it.garmentId) },
        leastWornGarmentIds = sortedByWear.takeLast(TOP_N).reversed().map { GarmentId(it.garmentId) },
        favouriteBrandIds = breakdowns.brands.map { BrandId(it.brandId) },
        signatureColorIds = breakdowns.colors.map { ColorId(it.colorId) },
        favouriteCategoryIds = breakdowns.categories.map { CategoryId(it.categoryId) },
        wearsBySeason =
            breakdowns.bySeason
                .mapNotNull { row ->
                    runCatching { Season.valueOf(row.season) }.getOrNull()?.let { it to row.wearCount }
                }.toMap(),
        wearsByDressCode =
            breakdowns.byDressCode
                .mapNotNull { row ->
                    runCatching { DressCode.valueOf(row.dressCode) }.getOrNull()?.let { it to row.wearCount }
                }.toMap(),
        weekdayWearCount = breakdowns.weekdayWeekend.firstOrNull { !it.isWeekend }?.wearCount ?: 0,
        weekendWearCount = breakdowns.weekdayWeekend.firstOrNull { it.isWeekend }?.wearCount ?: 0,
        categoryWearCounts =
            extras.allCategoryCounts.map { CategoryWearCountEntry(CategoryId(it.categoryId), it.wearCount) },
        garmentVersatility =
            extras.versatility.map { GarmentVersatilityEntry(GarmentId(it.garmentId), it.outfitCount) },
        topGarmentCombinations =
            extras.combinations.map {
                GarmentCombinationEntry(GarmentId(it.garmentIdA), GarmentId(it.garmentIdB), it.pairCount)
            },
    )
}
