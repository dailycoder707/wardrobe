package com.wardrobe.app.feature.stats.insights

import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.GarmentCombinationEntry
import com.wardrobe.app.core.model.stats.GarmentVersatilityEntry
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.feature.stats.common.HeatmapBucketCache
import com.wardrobe.app.feature.stats.common.InsightItemUiModel
import com.wardrobe.app.feature.stats.common.charts.BarChartEntry
import com.wardrobe.app.feature.stats.common.charts.HeatmapCellUiModel
import com.wardrobe.app.feature.stats.common.recordTiming
import com.wardrobe.app.feature.stats.common.toInsightItem
import java.time.LocalDate

private const val TOP_N = 6
private const val RECENT_ACTIVITY_LIMIT = 10

fun buildCharts(
    usage: UsageStats,
    heatmap: List<WearHeatmapDay>,
    heatmapRange: DateRange,
): InsightsChartsUiState =
    recordTiming("insightsCharts") {
        val buckets = HeatmapBucketCache.getOrCompute(heatmap) { computeMonthlyWeeklyBuckets(heatmap) }
        InsightsChartsUiState(
            seasonDistribution = Season.entries.map { BarChartEntry(it.shortLabel(), usage.wearsBySeason[it] ?: 0) },
            dressCodeDistribution =
                DressCode.entries.map { BarChartEntry(it.shortLabel(), usage.wearsByDressCode[it] ?: 0) },
            weekdayVsWeekend =
                listOf(
                    BarChartEntry("Weekdays", usage.weekdayWearCount),
                    BarChartEntry("Weekends", usage.weekendWearCount),
                ),
            monthlyWear = buckets.monthly,
            weeklyWear = buckets.weekly,
            heatmapCells = heatmap.map { HeatmapCellUiModel(it.date, it.wearCount) },
            heatmapRangeStart = heatmapRange.start,
            heatmapRangeEnd = heatmapRange.end,
        )
    }

internal fun buildLists(
    garments: List<Garment>,
    outfits: List<Outfit>,
    lists: InsightsListsSource,
    activity: List<WearEvent>,
    today: LocalDate,
    usage: UsageStats,
): InsightsListsUiState =
    recordTiming("insightsLists") { buildListsInternal(garments, outfits, lists, activity, today, usage) }

private fun buildListsInternal(
    garments: List<Garment>,
    outfits: List<Outfit>,
    lists: InsightsListsSource,
    activity: List<WearEvent>,
    today: LocalDate,
    usage: UsageStats,
): InsightsListsUiState {
    val garmentsById = garments.associateBy { it.id }
    val outfitsById = outfits.associateBy { it.id }
    val sortedByWear = lists.costPerWear.sortedByDescending { it.totalWearCount }
    val everWorn = sortedByWear.filter { it.totalWearCount > 0 }

    return InsightsListsUiState(
        mostWorn =
            everWorn.take(TOP_N).mapNotNull { entry ->
                garmentsById[entry.garmentId]?.toInsightItem(wearCountMetric(entry.totalWearCount))
            },
        leastWorn =
            everWorn.takeLast(TOP_N).reversed().mapNotNull { entry ->
                garmentsById[entry.garmentId]?.toInsightItem(wearCountMetric(entry.totalWearCount))
            },
        dormantAndNeverWorn =
            lists.dormant
                .sortedWith(compareBy(nullsFirst()) { it.lastWornDate })
                .take(TOP_N * 2)
                .mapNotNull { item ->
                    garmentsById[item.garmentId]?.toInsightItem(dormantMetric(item.lastWornDate, today))
                },
        costPerWear = leastCostPerWear(sortedByWear, garmentsById),
        garmentsMissingOutfits =
            lists.missingOutfits.take(TOP_N * 2).mapNotNull { id ->
                garmentsById[id]?.toInsightItem("Not in any look yet")
            },
        outfitsNeverWorn =
            lists.neverWornOutfits.take(TOP_N * 2).mapNotNull { id ->
                outfitsById[id]?.toInsightItem("Never worn", garmentsById)
            },
        frequentlyRepeatedLooks =
            lists.repeatedOutfits.mapNotNull { repeated ->
                outfitsById[repeated.outfitId]?.toInsightItem(wearCountMetric(repeated.wearCount), garmentsById)
            },
        recentActivity =
            activity
                .sortedByDescending { it.date }
                .take(RECENT_ACTIVITY_LIMIT)
                .mapNotNull { event -> event.toInsightItem(garmentsById, outfitsById, today) },
        favoriteCombinations = favoriteCombinations(usage.topGarmentCombinations, garmentsById),
        mostVersatile = mostVersatile(usage.garmentVersatility, garmentsById),
        leastVersatile = leastVersatile(usage.garmentVersatility, garmentsById),
        costPerWearLeastValuable = mostCostPerWear(sortedByWear, garmentsById),
    )
}

private fun leastCostPerWear(
    sortedByWear: List<CostPerWearEntry>,
    garmentsById: Map<GarmentId, Garment>,
): List<InsightItemUiModel> =
    sortedByWear
        .filter { it.costPerWear != null }
        .sortedBy { it.costPerWear }
        .take(TOP_N)
        .mapNotNull { entry ->
            val garment = garmentsById[entry.garmentId] ?: return@mapNotNull null
            garment.toInsightItem(costPerWearMetric(entry.costPerWear, garment.price?.currencyCode))
        }

private fun mostCostPerWear(
    sortedByWear: List<CostPerWearEntry>,
    garmentsById: Map<GarmentId, Garment>,
): List<InsightItemUiModel> =
    sortedByWear
        .filter { it.costPerWear != null }
        .sortedByDescending { it.costPerWear }
        .take(TOP_N)
        .mapNotNull { entry ->
            val garment = garmentsById[entry.garmentId] ?: return@mapNotNull null
            garment.toInsightItem(costPerWearMetric(entry.costPerWear, garment.price?.currencyCode))
        }

private fun favoriteCombinations(
    combinations: List<GarmentCombinationEntry>,
    garmentsById: Map<GarmentId, Garment>,
): List<InsightItemUiModel> =
    combinations.take(TOP_N).mapNotNull { combo ->
        val first = garmentsById[combo.garmentIdA] ?: return@mapNotNull null
        val second = garmentsById[combo.garmentIdB] ?: return@mapNotNull null
        val firstName = first.name?.takeUnless { it.isBlank() } ?: "Item"
        val secondName = second.name?.takeUnless { it.isBlank() } ?: "Item"
        first.toInsightItem("worn together ${combo.pairCount}x").copy(title = "$firstName + $secondName")
    }

private fun mostVersatile(
    versatility: List<GarmentVersatilityEntry>,
    garmentsById: Map<GarmentId, Garment>,
): List<InsightItemUiModel> =
    versatility
        .sortedByDescending { it.outfitCount }
        .take(TOP_N)
        .mapNotNull { entry -> garmentsById[entry.garmentId]?.toInsightItem(outfitCountMetric(entry.outfitCount)) }

private fun leastVersatile(
    versatility: List<GarmentVersatilityEntry>,
    garmentsById: Map<GarmentId, Garment>,
): List<InsightItemUiModel> =
    versatility
        .filter { it.outfitCount > 0 }
        .sortedBy { it.outfitCount }
        .take(TOP_N)
        .mapNotNull { entry -> garmentsById[entry.garmentId]?.toInsightItem(outfitCountMetric(entry.outfitCount)) }

private fun outfitCountMetric(count: Int): String = if (count == 1) "in 1 outfit" else "in $count outfits"

private fun WearEvent.toInsightItem(
    garmentsById: Map<GarmentId, Garment>,
    outfitsById: Map<OutfitId, Outfit>,
    today: LocalDate,
): InsightItemUiModel? {
    val metric = relativeDateMetric(date, today)
    return garmentId?.let { garmentsById[it]?.toInsightItem(metric) }
        ?: outfitId?.let { outfitsById[it]?.toInsightItem(metric, garmentsById) }
}
