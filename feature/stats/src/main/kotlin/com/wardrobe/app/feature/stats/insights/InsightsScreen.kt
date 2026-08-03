package com.wardrobe.app.feature.stats.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.feature.stats.common.InsightItemUiModel
import com.wardrobe.app.feature.stats.common.InsightSectionCard
import com.wardrobe.app.feature.stats.common.charts.InsightRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onOpenGarment: (Long) -> Unit,
    onOpenOutfit: (Long) -> Unit,
    onOpenStory: () -> Unit,
    onOpenHealth: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onOpenItem: (InsightItemUiModel) -> Unit = { item ->
        if (item.isOutfit) onOpenOutfit(item.id) else onOpenGarment(item.id)
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Insights") }) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            InsightsContent(
                state = state,
                onWindowChange = viewModel::onWindowChange,
                onOpenItem = onOpenItem,
                onOpenStory = onOpenStory,
                onOpenHealth = onOpenHealth,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun InsightsContent(
    state: InsightsUiState,
    onWindowChange: (StatsWindow) -> Unit,
    onOpenItem: (InsightItemUiModel) -> Unit,
    onOpenStory: () -> Unit,
    onOpenHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MoreScreensRow(onOpenStory, onOpenHealth) }
        item { WindowSelector(state.window, onWindowChange) }
        item { UsageOverviewSection(state) }
        item { FavoritesSection(state) }
        item { DistributionSections(state) }
        item { ActivityChartSections(state) }
        insightListSections(state.lists, onOpenItem)
    }
}

@Composable
private fun MoreScreensRow(
    onOpenStory: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onOpenStory, modifier = Modifier.weight(1f)) { Text("Your Wardrobe Story") }
        OutlinedButton(onClick = onOpenHealth, modifier = Modifier.weight(1f)) { Text("Wardrobe Health") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowSelector(
    selected: StatsWindow,
    onWindowChange: (StatsWindow) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsWindow.entries.forEach { window ->
            FilterChip(
                selected = window == selected,
                onClick = { onWindowChange(window) },
                label = { Text(window.displayLabel()) },
            )
        }
    }
}

private fun StatsWindow.displayLabel(): String =
    when (this) {
        StatsWindow.ONE_MONTH -> "1 Month"
        StatsWindow.SIX_MONTHS -> "6 Months"
        StatsWindow.ONE_YEAR -> "1 Year"
        StatsWindow.ALL_TIME -> "All Time"
    }

private fun LazyListScope.insightListSections(
    lists: InsightsListsUiState,
    onOpenItem: (InsightItemUiModel) -> Unit,
) {
    insightListSection("Most Worn", "Your everyday favorites.", lists.mostWorn, onOpenItem)
    insightListSection("Least Worn", "Pieces that could use a little more love.", lists.leastWorn, onOpenItem)
    insightListSection(
        "Waiting to Be Worn",
        "Nothing wrong with these — just haven't been reached for lately.",
        lists.dormantAndNeverWorn,
        onOpenItem,
    )
    insightListSection("Best Value", "Getting the most wears out of what you paid.", lists.costPerWear, onOpenItem)
    insightListSection(
        "Missing an Outfit",
        "Try building a look around one of these.",
        lists.garmentsMissingOutfits,
        onOpenItem,
    )
    insightListSection("Looks You Haven't Worn Yet", "Saved, ready, and waiting.", lists.outfitsNeverWorn, onOpenItem)
    insightListSection(
        "Go-To Looks",
        "The combinations you keep coming back to.",
        lists.frequentlyRepeatedLooks,
        onOpenItem,
    )
    insightListSection("Recent Activity", "What you've logged lately.", lists.recentActivity, onOpenItem)
    insightListSection(
        "Favorite Combinations",
        "Pairings you reach for again and again.",
        lists.favoriteCombinations,
        onOpenItem,
    )
    insightListSection("Most Versatile", "These work into the most looks.", lists.mostVersatile, onOpenItem)
    insightListSection("Least Versatile", "These haven't found many looks yet.", lists.leastVersatile, onOpenItem)
    insightListSection(
        "Highest Cost Per Wear",
        "These could use a few more wears to earn their keep.",
        lists.costPerWearLeastValuable,
        onOpenItem,
    )
}

private fun LazyListScope.insightListSection(
    title: String,
    subtitle: String,
    items: List<InsightItemUiModel>,
    onOpenItem: (InsightItemUiModel) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        InsightSectionCard(title = title, subtitle = subtitle) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { insightItem ->
                    InsightRow(item = insightItem, onClick = { onOpenItem(insightItem) })
                }
            }
        }
    }
}
