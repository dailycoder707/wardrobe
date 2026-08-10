package com.wardrobe.app.feature.stats.insights

import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.feature.stats.common.InsightItemUiModel
import com.wardrobe.app.feature.stats.common.charts.BarChartEntry
import com.wardrobe.app.feature.stats.common.charts.HeatmapCellUiModel
import java.time.LocalDate

/** Distribution/frequency charts — one [BarChartEntry] list per question this
 * screen answers, kept as a nested holder so [InsightsUiState] itself doesn't
 * grow one field per chart. */
data class InsightsChartsUiState(
    val seasonDistribution: List<BarChartEntry> = emptyList(),
    val dressCodeDistribution: List<BarChartEntry> = emptyList(),
    val weekdayVsWeekend: List<BarChartEntry> = emptyList(),
    val monthlyWear: List<BarChartEntry> = emptyList(),
    val weeklyWear: List<BarChartEntry> = emptyList(),
    val heatmapCells: List<HeatmapCellUiModel> = emptyList(),
    val heatmapRangeStart: LocalDate = LocalDate.now(),
    val heatmapRangeEnd: LocalDate = LocalDate.now(),
    /** M21 — composition-based (own it or not), not wear-based like the
     * distributions above; empty when nothing is tagged, never a fabricated
     * placeholder bar. */
    val materialDistribution: List<BarChartEntry> = emptyList(),
    val fabricDistribution: List<BarChartEntry> = emptyList(),
    /** M21 — includes real occasions with zero garments (the coverage gap
     * itself), same reasoning as `ClosetGap`'s season/dress-code coverage. */
    val occasionCoverage: List<BarChartEntry> = emptyList(),
    /** M21 Part 6 — honest missing-metadata disclosure: how many active
     * garments have no occasion tagged at all, rather than silently letting
     * [occasionCoverage] imply full coverage. */
    val garmentsWithoutOccasionCount: Int = 0,
)

/** Every tap-to-navigate insight list this screen shows, grouped for the same
 * reason as [InsightsChartsUiState]. */
data class InsightsListsUiState(
    val mostWorn: List<InsightItemUiModel> = emptyList(),
    val leastWorn: List<InsightItemUiModel> = emptyList(),
    val dormantAndNeverWorn: List<InsightItemUiModel> = emptyList(),
    val costPerWear: List<InsightItemUiModel> = emptyList(),
    val garmentsMissingOutfits: List<InsightItemUiModel> = emptyList(),
    val outfitsNeverWorn: List<InsightItemUiModel> = emptyList(),
    val frequentlyRepeatedLooks: List<InsightItemUiModel> = emptyList(),
    val recentActivity: List<InsightItemUiModel> = emptyList(),
    /** Phase 9 additions — reusing the same [InsightItemUiModel] shape every
     * other list here already uses. */
    val favoriteCombinations: List<InsightItemUiModel> = emptyList(),
    val mostVersatile: List<InsightItemUiModel> = emptyList(),
    val leastVersatile: List<InsightItemUiModel> = emptyList(),
    val costPerWearLeastValuable: List<InsightItemUiModel> = emptyList(),
)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val window: StatsWindow = StatsWindow.ONE_YEAR,
    val usagePercent: Int = 0,
    val totalActiveGarments: Int = 0,
    val wornAtLeastOnce: Int = 0,
    val favoriteBrandNames: List<String> = emptyList(),
    val favoriteColorNames: List<String> = emptyList(),
    val favoriteCategoryNames: List<String> = emptyList(),
    val charts: InsightsChartsUiState = InsightsChartsUiState(),
    val lists: InsightsListsUiState = InsightsListsUiState(),
    /** Phase 9 — Style Insights' per-slot favorite breakdown, each `null`
     * when the wardrobe has nothing worn yet in that slot; and
     * `100 - usagePercent`, spelled out so the UI never has to subtract. */
    val favoriteFootwearName: String? = null,
    val favoriteBagName: String? = null,
    val favoriteJewelryName: String? = null,
    val favoriteAccessoryName: String? = null,
    val unusedWardrobePercent: Int = 0,
)
