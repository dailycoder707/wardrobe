package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.AiActivityBanner
import com.wardrobe.app.core.ui.components.AiActivityTone
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.ConfirmationToastState
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
    onOpenAiProviders: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onAddGarment: () -> Unit,
    onTryOn: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toastState = rememberConfirmationToastState()

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { toastState.show(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            RecommendationsTopBar(
                onOpenWeatherSettings = onOpenWeatherSettings,
                onOpenWardrobeSync = onOpenWardrobeSync,
                onOpenAiProviders = onOpenAiProviders,
                onOpenPreferences = onOpenPreferences,
            )
        },
    ) { innerPadding ->
        RecommendationsBody(
            state = state,
            viewModel = viewModel,
            toastState = toastState,
            onOpenGarment = onOpenGarment,
            onOpenPreview = onOpenPreview,
            onOpenCapsules = onOpenCapsules,
            onOpenDuplicates = onOpenDuplicates,
            onAddGarment = onAddGarment,
            onTryOn = onTryOn,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationsTopBar(
    onOpenWeatherSettings: () -> Unit,
    onOpenWardrobeSync: () -> Unit,
    onOpenAiProviders: () -> Unit,
    onOpenPreferences: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text("Today's Looks") },
        actions = {
            TextButton(onClick = onOpenWeatherSettings) { Text("Weather") }
            TextButton(onClick = onOpenWardrobeSync) { Text("Sync") }
            TextButton(onClick = onOpenAiProviders) { Text("AI") }
            TextButton(onClick = onOpenPreferences) { Text("Preferences") }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun RecommendationsBody(
    state: RecommendationsUiState,
    viewModel: RecommendationsViewModel,
    toastState: ConfirmationToastState,
    onOpenGarment: (Long) -> Unit,
    onOpenPreview: (List<Long>) -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onAddGarment: () -> Unit,
    onTryOn: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.isError -> {
                EmptyState(
                    icon = Icons.Outlined.Checkroom,
                    headline = "Something went wrong",
                    supportingText = state.errorMessage ?: "Couldn't generate a recommendation.",
                    actionLabel = "Try again",
                    onAction = viewModel::generate,
                )
            }

            // M22 fix: these two cases used to share one message implying
            // "you're a few pieces short" even for a completely empty
            // wardrobe — genuinely different situations with different
            // honest copy.
            state.isEmpty && state.hasNoGarments -> {
                EmptyState(
                    icon = Icons.Outlined.Checkroom,
                    headline = "Add your first items to get outfit recommendations",
                    supportingText = "Once you've added a few garments, we'll suggest outfits built from them.",
                    actionLabel = "Add garments",
                    onAction = onAddGarment,
                )
            }

            state.isEmpty -> {
                EmptyState(
                    icon = Icons.Outlined.Checkroom,
                    headline = "Not enough wardrobe items for a complete outfit",
                    supportingText = "Add a few more items to your closet and check back.",
                    actionLabel = "Add garments",
                    onAction = onAddGarment,
                )
            }

            else -> {
                RecommendationsContent(
                    state = state,
                    onSelect = viewModel::selectSuggestion,
                    onOpenGarment = onOpenGarment,
                    onOpenPreview = onOpenPreview,
                    onReplaceSlot = viewModel::replaceSlot,
                    onShowAnother = viewModel::showAnother,
                    onWearToday = viewModel::wearToday,
                    onSave = viewModel::saveSelected,
                    onFavorite = viewModel::favoriteSelected,
                    onOpenCapsules = onOpenCapsules,
                    onOpenDuplicates = onOpenDuplicates,
                    onOccasionSelected = viewModel::onOccasionSelected,
                    onTryOn = onTryOn,
                )
            }
        }
        ConfirmationToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
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
    onShowAnother: () -> Unit,
    onWearToday: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOccasionSelected: (OccasionId?) -> Unit,
    onTryOn: (List<Long>) -> Unit,
) {
    val selected = state.selected ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TodaysContextHeader(state = state, onOccasionSelected = onOccasionSelected) }
        if (state.isCloudStylingActive) {
            item { AiActivityBanner(label = "Using Cloud AI to style your outfit…", tone = AiActivityTone.RUNNING) }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.suggestions.size) { index ->
                    SuggestionTab(index, index == state.selectedIndex, onSelect)
                }
            }
        }
        item { CapsulesDuplicatesRow(onOpenCapsules = onOpenCapsules, onOpenDuplicates = onOpenDuplicates) }
        item { WhyThisSection(selected.reasonBullets) }
        item { if (selected.provenance != null) AiStyledBadge(selected) else RuleBasedBadge() }
        item { PreviewTryOnRow(selected = selected, onOpenPreview = onOpenPreview, onTryOn = onTryOn) }
        items(selected.items) { item ->
            RecommendedItemCard(
                item,
                onClick = { onOpenGarment(item.tile.id) },
                onReplace = { onReplaceSlot(item.slot) },
            )
        }
        if (selected.accessoryItems.isNotEmpty() || selected.jewelryItems.isNotEmpty()) {
            item { AlsoConsiderWearingSection(selected.accessoryItems + selected.jewelryItems) }
        }
        item { QuickActionsRow(onShowAnother, onWearToday, onSave, onFavorite) }
    }
}

@Composable
private fun CapsulesDuplicatesRow(
    onOpenCapsules: () -> Unit,
    onOpenDuplicates: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onOpenCapsules) { Text("Capsules") }
        TextButton(onClick = onOpenDuplicates) { Text("Duplicates") }
    }
}

@Composable
private fun PreviewTryOnRow(
    selected: RecommendedOutfitUiModel,
    onOpenPreview: (List<Long>) -> Unit,
    onTryOn: (List<Long>) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onOpenPreview(selected.items.map { it.tile.id }) }) {
            Text("Preview this look")
        }
        TextButton(onClick = { onTryOn(selected.items.map { it.tile.id }) }) {
            Text("Try On")
        }
    }
}

@Composable
private fun SuggestionTab(
    index: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val label = if (index == 0) "Best Match" else "Alternative $index"
    TextButton(onClick = { onSelect(index) }) {
        Text(
            label,
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
    onShowAnother: () -> Unit,
    onWearToday: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onWearToday) { Text("Wear Today") }
        TextButton(onClick = onSave) { Text("Save") }
        TextButton(onClick = onFavorite) { Text("Favorite") }
        TextButton(onClick = onShowAnother) {
            Text("Show Another")
        }
    }
}
