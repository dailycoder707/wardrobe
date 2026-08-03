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
