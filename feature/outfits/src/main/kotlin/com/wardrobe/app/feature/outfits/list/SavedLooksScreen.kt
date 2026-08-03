package com.wardrobe.app.feature.outfits.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.OutfitSort
import com.wardrobe.app.core.model.outfit.OutfitSortField
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.ConfirmationToastState
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import com.wardrobe.app.core.ui.components.rememberConfirmationToastState

private data class SavedLooksTopBarState(
    val isSearchActive: Boolean,
    val searchQuery: String,
    val activeFilterCount: Int,
)

private data class SavedLooksTopBarActions(
    val onSearchQueryChange: (String) -> Unit,
    val onSearchActiveChange: (Boolean) -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSort: () -> Unit,
    val onOpenRecommendations: () -> Unit,
)

private data class SavedLooksGridActions(
    val onOpenOutfit: (Long) -> Unit,
    val onCreateLook: () -> Unit,
    val onTryOnOutfit: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLooksScreen(
    onOpenOutfit: (Long) -> Unit,
    onCreateLook: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onTryOnOutfit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedLooksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    val toastState = rememberConfirmationToastState()

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastState.show(it)
            viewModel.onToastShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SavedLooksTopBar(
                state = SavedLooksTopBarState(isSearchActive, state.searchQuery, state.filters.activeCount),
                actions =
                    SavedLooksTopBarActions(
                        onSearchQueryChange = viewModel::onSearchQueryChange,
                        onSearchActiveChange = { isSearchActive = it },
                        onOpenFilters = { isFilterSheetOpen = true },
                        onOpenSort = { isSortSheetOpen = true },
                        onOpenRecommendations = onOpenRecommendations,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLook) {
                Icon(Icons.Filled.Add, contentDescription = "Create Look")
            }
        },
    ) { innerPadding ->
        SavedLooksContent(
            state = state,
            toastState = toastState,
            actions = SavedLooksGridActions(onOpenOutfit, onCreateLook, onTryOnOutfit),
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    SavedLooksOverlays(
        state = state,
        viewModel = viewModel,
        isFilterSheetOpen = isFilterSheetOpen,
        onFilterSheetDismissed = { isFilterSheetOpen = false },
        isSortSheetOpen = isSortSheetOpen,
        onSortSheetDismissed = { isSortSheetOpen = false },
    )
}

@Composable
private fun SavedLooksContent(
    state: SavedLooksUiState,
    toastState: ConfirmationToastState,
    actions: SavedLooksGridActions,
    viewModel: SavedLooksViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.isEmptyLibrary -> {
                EmptyState(
                    icon = Icons.Outlined.Checkroom,
                    headline = "No looks saved yet",
                    supportingText = "Build one, or save a suggestion from Home.",
                    actionLabel = "Create Look",
                    onAction = actions.onCreateLook,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.isEmptyResult -> {
                EmptyState(
                    icon = Icons.Filled.Search,
                    headline = "No matches",
                    supportingText = "Try a different search or clear your filters.",
                    actionLabel = if (state.filters.activeCount > 0) "Clear filters" else null,
                    onAction = viewModel::onClearFilters,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.outfits, key = { it.id }) { outfit ->
                        OutfitCard(
                            outfit = outfit,
                            onClick = { actions.onOpenOutfit(outfit.id) },
                            onLongClick = { viewModel.onDuplicate(outfit.id) },
                            onFavoriteClick = { viewModel.onToggleFavorite(outfit.id, !outfit.isFavorite) },
                            onTryOnClick = { actions.onTryOnOutfit(outfit.id) },
                        )
                    }
                }
            }
        }

        ConfirmationToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SavedLooksOverlays(
    state: SavedLooksUiState,
    viewModel: SavedLooksViewModel,
    isFilterSheetOpen: Boolean,
    onFilterSheetDismissed: () -> Unit,
    isSortSheetOpen: Boolean,
    onSortSheetDismissed: () -> Unit,
) {
    if (isFilterSheetOpen) {
        SavedLooksFilterSheet(
            filters = state.filters,
            occasions = state.occasionOptions,
            onFiltersChange = viewModel::onFiltersChange,
            onDismiss = onFilterSheetDismissed,
        )
    }

    if (isSortSheetOpen) {
        SavedLooksSortSheet(
            sort = state.sort,
            onSortChange = viewModel::onSortChange,
            onDismiss = onSortSheetDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedLooksTopBar(
    state: SavedLooksTopBarState,
    actions: SavedLooksTopBarActions,
) {
    TopAppBar(
        title = {
            if (state.isSearchActive) {
                TextField(
                    value = state.searchQuery,
                    onValueChange = actions.onSearchQueryChange,
                    placeholder = { Text("Search saved looks") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Saved Looks", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            IconButton(onClick = actions.onOpenRecommendations) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Today's Looks")
            }
            IconButton(onClick = { actions.onSearchActiveChange(!state.isSearchActive) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = actions.onOpenFilters) {
                val filterDescription =
                    if (state.activeFilterCount > 0) "Filters, ${state.activeFilterCount} active" else "Filters"
                Icon(Icons.Filled.FilterList, contentDescription = filterDescription)
            }
            IconButton(onClick = actions.onOpenSort) {
                Icon(Icons.Filled.Sort, contentDescription = "Sort")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SavedLooksFilterSheet(
    filters: SavedLooksFilterState,
    occasions: List<Occasion>,
    onFiltersChange: (SavedLooksFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Favorites only", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = filters.favoriteOnly,
                    onCheckedChange = { onFiltersChange(filters.copy(favoriteOnly = it)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show archived", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = filters.showArchived,
                    onCheckedChange = { onFiltersChange(filters.copy(showArchived = it)) },
                )
            }
            Text("Occasion", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                occasions.forEach { occasion ->
                    WardrobeFilterChip(
                        label = occasion.name,
                        selected = filters.occasionId == occasion.id,
                        onClick = {
                            onFiltersChange(
                                filters.copy(occasionId = if (filters.occasionId == occasion.id) null else occasion.id),
                            )
                        },
                    )
                }
            }
            TextButton(onClick = { onFiltersChange(SavedLooksFilterState.EMPTY) }) { Text("Clear all") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedLooksSortSheet(
    sort: OutfitSort,
    onSortChange: (OutfitSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sort by", style = MaterialTheme.typography.titleLarge)
            OutfitSortField.entries.forEach { field ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSortChange(sort.copy(field = field)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sort.field == field, onClick = { onSortChange(sort.copy(field = field)) })
                    Text(field.displayName())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { onSortChange(sort.copy(direction = SortDirection.ASCENDING)) }) {
                    Text(if (sort.direction == SortDirection.ASCENDING) "● Ascending" else "Ascending")
                }
                TextButton(onClick = { onSortChange(sort.copy(direction = SortDirection.DESCENDING)) }) {
                    Text(if (sort.direction == SortDirection.DESCENDING) "● Descending" else "Descending")
                }
            }
        }
    }
}

private fun OutfitSortField.displayName(): String =
    when (this) {
        OutfitSortField.RECENTLY_ADDED -> "Recently added"
        OutfitSortField.RECENTLY_WORN -> "Recently worn"
        OutfitSortField.MOST_WORN -> "Most worn"
        OutfitSortField.ALPHABETICAL -> "Alphabetical"
    }
