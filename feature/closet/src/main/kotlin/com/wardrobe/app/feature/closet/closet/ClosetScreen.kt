package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.ConfirmationToastState
import com.wardrobe.app.core.ui.components.EmptyState
import com.wardrobe.app.core.ui.components.rememberConfirmationToastState

/** [onTakePhoto]/[onImportStarted] bagged since a single-callback-per-line
 * signature would push this composable's parameter count over detekt's
 * threshold once combined with the screen's other navigation/state params. */
data class ClosetAddActions(
    val onTakePhoto: () -> Unit,
    val onImportStarted: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosetScreen(
    onOpenGarment: (Long) -> Unit,
    addActions: ClosetAddActions,
    modifier: Modifier = Modifier,
    initialFavoriteFilter: Boolean = false,
    initialSearchFocus: Boolean = false,
    viewModel: ClosetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(initialSearchFocus) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var isAddSheetOpen by remember { mutableStateOf(false) }
    val toastState = rememberConfirmationToastState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (initialFavoriteFilter) viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(favoriteOnly = true))
    }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastState.show(it)
            viewModel.onToastShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(onClick = { isAddSheetOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add to Wardrobe")
                }
            }
        },
        topBar = {
            ClosetScreenTopBar(
                state = state,
                viewModel = viewModel,
                isSearchActive = isSearchActive,
                actions =
                    ClosetTopBarActions(
                        onSearchActiveChange = { isSearchActive = it },
                        onOpenFilters = { isFilterSheetOpen = true },
                        onOpenSort = { isSortSheetOpen = true },
                        onDeleteSelected = buildDeleteSelectedAction(viewModel, snackbarHostState, scope),
                    ),
            )
        },
    ) { innerPadding ->
        ClosetScreenBody(
            state = state,
            innerPadding = innerPadding,
            toastState = toastState,
            onOpenGarment = onOpenGarment,
            onAddFirstItem = { isAddSheetOpen = true },
            viewModel = viewModel,
        )
    }

    ClosetScreenSheets(
        state = state,
        viewModel = viewModel,
        isFilterSheetOpen = isFilterSheetOpen,
        onDismissFilterSheet = { isFilterSheetOpen = false },
        isSortSheetOpen = isSortSheetOpen,
        onDismissSortSheet = { isSortSheetOpen = false },
    )

    ClosetAddToWardrobeOverlay(
        isOpen = isAddSheetOpen,
        onDismiss = { isAddSheetOpen = false },
        addActions = addActions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosetScreenTopBar(
    state: ClosetUiState,
    viewModel: ClosetViewModel,
    isSearchActive: Boolean,
    actions: ClosetTopBarActions,
) {
    if (state.isSelectionMode) {
        SelectionTopBar(
            selectedCount = state.selectedIds.size,
            onClear = viewModel.selection::clear,
            onDelete = actions.onDeleteSelected,
            onFavorite = viewModel.selection::favoriteSelected,
        )
    } else {
        ClosetTopBar(
            isSearchActive = isSearchActive,
            searchQuery = state.searchQuery,
            recentSearches = state.recentSearches,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchSubmit = { viewModel.onSearchSubmit(it) },
            onSearchActiveChange = actions.onSearchActiveChange,
            onClearSearchHistory = viewModel::onClearSearchHistory,
            activeFilterCount = state.filters.activeCount,
            onOpenFilters = actions.onOpenFilters,
            onOpenSort = actions.onOpenSort,
            gridColumnCount = state.gridColumnCount,
            onGridColumnCountChange = viewModel::onGridColumnCountChange,
        )
    }
}

@Composable
private fun ClosetScreenBody(
    state: ClosetUiState,
    innerPadding: PaddingValues,
    toastState: ConfirmationToastState,
    onOpenGarment: (Long) -> Unit,
    onAddFirstItem: () -> Unit,
    viewModel: ClosetViewModel,
) {
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.filters.activeCount > 0) {
                ActiveFilterChipsRow(
                    filters = state.filters,
                    options = state.filterOptions,
                    onFiltersChange = viewModel::onFiltersChange,
                    onClearAll = viewModel::onClearFilters,
                )
            }
            ClosetScreenContent(state, onOpenGarment, onAddFirstItem, viewModel)
        }

        ConfirmationToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun ClosetScreenContent(
    state: ClosetUiState,
    onOpenGarment: (Long) -> Unit,
    onAddFirstItem: () -> Unit,
    viewModel: ClosetViewModel,
) {
    when {
        state.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.isEmptyCloset -> {
            EmptyState(
                icon = Icons.Filled.Checkroom,
                headline = "Your closet is empty",
                supportingText = "Items you add will show up here.",
                actionLabel = "Add your first item",
                onAction = onAddFirstItem,
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.isEmptyResult -> {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                headline = "No matches",
                supportingText = "Try a different search or clear your filters.",
                actionLabel = if (state.filters.activeCount > 0) "Clear filters" else null,
                onAction = viewModel::onClearFilters,
                modifier = Modifier.fillMaxSize(),
            )
        }

        else -> {
            ClosetGrid(
                garments = state.garments,
                columnCount = state.gridColumnCount,
                selectedIds = state.selectedIds,
                isSelectionMode = state.isSelectionMode,
                onColumnCountChange = viewModel::onGridColumnCountChange,
                onOpenGarment = onOpenGarment,
                onToggleSelection = viewModel.selection::toggle,
                onEnterSelectionMode = viewModel.selection::enter,
                onFavoriteClick = viewModel::onToggleFavorite,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosetTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    recentSearches: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onClearSearchHistory: () -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    gridColumnCount: Int,
    onGridColumnCountChange: (Int) -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                if (isSearchActive) {
                    ClosetSearchField(searchQuery, onSearchQueryChange, onSearchSubmit)
                } else {
                    Text("Closet", style = MaterialTheme.typography.titleLarge)
                }
            },
            navigationIcon = {
                if (isSearchActive) {
                    IconButton(onClick = {
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                }
            },
            actions = {
                if (!isSearchActive) {
                    ClosetTopBarActions(
                        onSearchActiveChange = onSearchActiveChange,
                        activeFilterCount = activeFilterCount,
                        onOpenFilters = onOpenFilters,
                        onOpenSort = onOpenSort,
                        gridColumnCount = gridColumnCount,
                        onGridColumnCountChange = onGridColumnCountChange,
                    )
                }
            },
        )

        if (isSearchActive && searchQuery.isBlank() && recentSearches.isNotEmpty()) {
            RecentSearchesRow(
                recentSearches = recentSearches,
                onSelect = {
                    onSearchQueryChange(it)
                    onSearchSubmit(it)
                },
                onClearHistory = onClearSearchHistory,
            )
        }
    }
}

@Composable
private fun ClosetSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search your closet") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(searchQuery) }),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun ClosetTopBarActions(
    onSearchActiveChange: (Boolean) -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    gridColumnCount: Int,
    onGridColumnCountChange: (Int) -> Unit,
) {
    IconButton(onClick = { onSearchActiveChange(true) }) {
        Icon(Icons.Filled.Search, contentDescription = "Search")
    }
    IconButton(onClick = onOpenFilters) {
        Icon(
            Icons.Filled.FilterList,
            contentDescription = if (activeFilterCount > 0) "Filters, $activeFilterCount active" else "Filters",
            tint =
                if (activeFilterCount > 0) {
                    WardrobeTheme.extendedColors.accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
    IconButton(onClick = onOpenSort) {
        Icon(Icons.Filled.Sort, contentDescription = "Sort")
    }
    GridDensityStepper(gridColumnCount, onGridColumnCountChange)
}

@Composable
private fun GridDensityStepper(
    columnCount: Int,
    onChange: (Int) -> Unit,
) {
    IconButton(onClick = {
        onChange(if (columnCount >= MAX_STEPPER_COLUMNS) MIN_STEPPER_COLUMNS else columnCount + 1)
    }) {
        Icon(Icons.Filled.GridView, contentDescription = "Change grid size, currently $columnCount columns")
    }
}

@Composable
private fun RecentSearchesRow(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent searches",
                style = MaterialTheme.typography.labelMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            TextButton(onClick = onClearHistory) { Text("Clear") }
        }
        recentSearches.forEach { query ->
            ListItem(
                headlineContent = { Text(query) },
                modifier = Modifier.fillMaxWidth().clickable { onSelect(query) },
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
        },
        actions = {
            IconButton(
                onClick = onFavorite,
            ) { Icon(Icons.Filled.Star, contentDescription = "Add selected to favorites") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
        },
    )
}

private const val MIN_STEPPER_COLUMNS = 2
private const val MAX_STEPPER_COLUMNS = 6
