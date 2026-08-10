package com.wardrobe.app.feature.stats.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import com.wardrobe.app.feature.stats.common.InsightSectionCard
import com.wardrobe.app.feature.stats.common.charts.BarChart
import com.wardrobe.app.feature.stats.common.charts.CalendarHeatmap
import com.wardrobe.app.feature.stats.common.charts.ProgressRing

@Composable
fun UsageOverviewSection(state: InsightsUiState) {
    InsightSectionCard(
        title = "Your Wardrobe at a Glance",
        subtitle = "How much of your closet you've actually worn.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(progress = state.usagePercent / PERCENT_SCALE, centerLabel = "${state.usagePercent}%")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${state.totalActiveGarments} pieces in your closet", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${state.wornAtLeastOnce} worn this window",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
                Text(
                    "${state.unusedWardrobePercent}% of your closet is unused this window",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun FavoritesSection(state: InsightsUiState) {
    val slotFavorites =
        listOfNotNull(
            state.favoriteFootwearName,
            state.favoriteBagName,
            state.favoriteJewelryName,
            state.favoriteAccessoryName,
        )
    val hasFavorites =
        state.favoriteBrandNames.isNotEmpty() ||
            state.favoriteColorNames.isNotEmpty() ||
            state.favoriteCategoryNames.isNotEmpty() ||
            slotFavorites.isNotEmpty()
    if (!hasFavorites) return
    InsightSectionCard(title = "Your Style", subtitle = "What you reach for most.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FavoriteChipRow("Colors", state.favoriteColorNames)
            FavoriteChipRow("Brands", state.favoriteBrandNames)
            FavoriteChipRow("Categories", state.favoriteCategoryNames)
            FavoriteChipRow("Footwear", listOfNotNull(state.favoriteFootwearName))
            FavoriteChipRow("Bags", listOfNotNull(state.favoriteBagName))
            FavoriteChipRow("Jewelry", listOfNotNull(state.favoriteJewelryName))
            FavoriteChipRow("Accessories", listOfNotNull(state.favoriteAccessoryName))
        }
    }
}

@Composable
private fun FavoriteChipRow(
    label: String,
    names: List<String>,
) {
    if (names.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEach { name -> WardrobeFilterChip(label = name, selected = false, onClick = {}) }
        }
    }
}

@Composable
fun DistributionSections(state: InsightsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InsightSectionCard(title = "Season Distribution", subtitle = "Which seasons you dress for most.") {
            BarChart(state.charts.seasonDistribution, modifier = Modifier.fillMaxWidth())
        }
        InsightSectionCard(
            title = "Dress Code Distribution",
            subtitle = "Casual, business, formal — where your wears go.",
        ) {
            BarChart(state.charts.dressCodeDistribution, modifier = Modifier.fillMaxWidth())
        }
        InsightSectionCard(title = "Weekday vs. Weekend", subtitle = "How your style shifts across the week.") {
            BarChart(state.charts.weekdayVsWeekend, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** M21 — "own it or not" composition mix, distinct from [DistributionSections]'
 * wear-based charts above: a material/fabric/occasion your closet covers
 * regardless of how often it's actually worn. Each chart is absent entirely
 * when nothing is tagged — never an empty chart implying a real measurement
 * (Part 12). */
@Composable
fun WardrobeMixSection(state: InsightsUiState) {
    val charts = state.charts
    val hasAnyMix =
        charts.materialDistribution.isNotEmpty() ||
            charts.fabricDistribution.isNotEmpty() ||
            charts.occasionCoverage.isNotEmpty()
    if (!hasAnyMix) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (charts.materialDistribution.isNotEmpty()) {
            InsightSectionCard(title = "Material Mix", subtitle = "What your closet is actually made of.") {
                BarChart(charts.materialDistribution, modifier = Modifier.fillMaxWidth())
            }
        }
        if (charts.fabricDistribution.isNotEmpty()) {
            InsightSectionCard(title = "Fabric Mix", subtitle = "Weave and construction, not fiber content.") {
                BarChart(charts.fabricDistribution, modifier = Modifier.fillMaxWidth())
            }
        }
        if (charts.occasionCoverage.isNotEmpty()) {
            InsightSectionCard(
                title = "Occasion Coverage",
                subtitle = "How many pieces you have ready for each occasion — including any at zero.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarChart(charts.occasionCoverage, modifier = Modifier.fillMaxWidth())
                    if (state.charts.garmentsWithoutOccasionCount > 0) {
                        Text(
                            "${state.charts.garmentsWithoutOccasionCount} items have no occasion tagged, " +
                                "so they aren't counted above.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WardrobeTheme.extendedColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityChartSections(state: InsightsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InsightSectionCard(title = "Monthly Wear", subtitle = "Your overall activity, month by month.") {
            BarChart(state.charts.monthlyWear, modifier = Modifier.fillMaxWidth())
        }
        InsightSectionCard(title = "Weekly Wear", subtitle = "The last few weeks at a glance.") {
            BarChart(state.charts.weeklyWear, modifier = Modifier.fillMaxWidth())
        }
        InsightSectionCard(title = "Usage Heatmap", subtitle = "Darker means you got dressed and logged it that day.") {
            CalendarHeatmap(
                cells = state.charts.heatmapCells,
                rangeStart = state.charts.heatmapRangeStart,
                rangeEnd = state.charts.heatmapRangeEnd,
            )
        }
    }
}

private const val PERCENT_SCALE = 100f
