package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.rememberConfirmationToastState

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    onOpenGarment: (Long) -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenPreview: (List<Long>) -> Unit,
    onOpenWeatherSettings: () -> Unit,
    onOpenWardrobeSync: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val toastState = rememberConfirmationToastState()

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { toastState.show(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Today's Looks") },
                actions = {
                    TextButton(onClick = onOpenWeatherSettings) { Text("Weather") }
                    TextButton(onClick = onOpenWardrobeSync) { Text("Sync") }
                    TextButton(onClick = onOpenPreferences) { Text("Preferences") }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.isEmpty -> {
                    EmptyState(
                        icon = Icons.Outlined.Checkroom,
                        headline = "Nothing to suggest yet",
                        supportingText = "Add a few more items to your closet and check back.",
                        actionLabel = "Try again",
                        onAction = viewModel::generate,
                    )
                }

                else -> {
                    RecommendationsContent(
                        state = state,
                        onSelect = viewModel::selectSuggestion,
                        onOpenGarment = onOpenGarment,
                        onOpenPreview = onOpenPreview,
                        onReplaceSlot = viewModel::replaceSlot,
                        onGenerateAnother = viewModel::generate,
                        onWearToday = viewModel::wearToday,
                        onSave = viewModel::saveSelected,
                        onFavorite = viewModel::favoriteSelected,
                        onOpenCapsules = onOpenCapsules,
                        onOpenDuplicates = onOpenDuplicates,
                    )
                }
            }
            ConfirmationToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun RecommendationsContent(
    state: RecommendationsUiState,
    onSelect: (Int) -> Unit,
    onOpenGarment: (Long) -> Unit,
    onOpenPreview: (List<Long>) -> Unit,
    onReplaceSlot: (OutfitSlot) -> Unit,
    onGenerateAnother: () -> Unit,
    onWearToday: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
) {
    val selected = state.selected ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.suggestions.size) { index ->
                    SuggestionTab(index, index == state.selectedIndex, onSelect)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenCapsules) { Text("Capsules") }
                TextButton(onClick = onOpenDuplicates) { Text("Duplicates") }
            }
        }
        item {
            Text(selected.explanation, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            TextButton(onClick = { onOpenPreview(selected.items.map { it.tile.id }) }) {
                Text("Preview this look")
            }
        }
        items(selected.items) { item ->
            RecommendedItemCard(
                item,
                onClick = { onOpenGarment(item.tile.id) },
                onReplace = { onReplaceSlot(item.slot) },
            )
        }
        if (selected.accessoryItems.isNotEmpty() || selected.jewelryItems.isNotEmpty()) {
            item {
                AlsoConsiderWearingSection(selected.accessoryItems + selected.jewelryItems)
            }
        }
        item {
            QuickActionsRow(onGenerateAnother, onWearToday, onSave, onFavorite)
        }
    }
}

@Composable
private fun SuggestionTab(
    index: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
) {
    TextButton(onClick = { onSelect(index) }) {
        Text(
            "Look ${index + 1}",
            style =
                if (selected) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            color = if (selected) WardrobeTheme.extendedColors.accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuickActionsRow(
    onGenerateAnother: () -> Unit,
    onWearToday: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onWearToday) { Text("Wear Today") }
        TextButton(onClick = onSave) { Text("Save") }
        TextButton(onClick = onFavorite) { Text("Favorite") }
        TextButton(onClick = onGenerateAnother) {
            Text("Another Look")
        }
    }
}
