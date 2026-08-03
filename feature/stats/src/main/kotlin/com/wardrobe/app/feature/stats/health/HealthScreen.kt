package com.wardrobe.app.feature.stats.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.feature.stats.common.InsightItemUiModel
import com.wardrobe.app.feature.stats.common.InsightSectionCard
import com.wardrobe.app.feature.stats.common.charts.InsightRow

/** Wardrobe Health — every advisory here is a gentle observation, never a
 * warning: no red numbers, no "you're wasting money," no streaks to keep
 * up. See `phase-5e-wardrobe-intelligence.md`'s philosophy note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onOpenGarment: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Wardrobe Health") }) },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.isEmpty -> {
                EmptyState(
                    icon = Icons.Outlined.Favorite,
                    headline = "Nothing to report yet",
                    supportingText =
                        "Log a few wears and check back — your wardrobe's health picture builds up over time.",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.advisories) { advisory -> HealthAdvisoryCard(advisory, onOpenGarment) }
                }
            }
        }
    }
}

@Composable
private fun HealthAdvisoryCard(
    advisory: HealthAdvisoryUiModel,
    onOpenGarment: (Long) -> Unit,
) {
    InsightSectionCard(title = advisory.title, subtitle = advisory.message) {
        if (advisory.items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                advisory.items.forEach { item ->
                    InsightRow(item = item, onClick = { onOpenItem(item, onOpenGarment) })
                }
            }
        }
    }
}

private fun onOpenItem(
    item: InsightItemUiModel,
    onOpenGarment: (Long) -> Unit,
) {
    if (!item.isOutfit) onOpenGarment(item.id)
}
