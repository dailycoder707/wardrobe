package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentSortField
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.ui.components.ConfirmationToastState
import com.wardrobe.app.feature.closet.addwardrobe.AddToWardrobeSheet

private val SORT_FIELD_LABELS =
    mapOf(
        GarmentSortField.RECENTLY_ADDED to "Recently Added",
        GarmentSortField.RECENTLY_WORN to "Recently Worn",
        GarmentSortField.ALPHABETICAL to "Alphabetical",
        GarmentSortField.BRAND to "Brand",
        GarmentSortField.COLOR to "Color",
        GarmentSortField.PRICE to "Price",
        GarmentSortField.WEAR_COUNT to "Wear Count",
        GarmentSortField.COST_PER_WEAR to "Cost Per Wear",
        GarmentSortField.FAVORITE_FIRST to "Favorite First",
    )

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClosetSortSheet(
    sort: GarmentSort,
    onSortChange: (GarmentSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                androidx.compose.ui.Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text("Sort by", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            GarmentSortField.entries.forEach { field ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = sort.field == field,
                                onClick = { onSortChange(sort.copy(field = field)) },
                            ).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sort.field == field, onClick = { onSortChange(sort.copy(field = field)) })
                    Text(SORT_FIELD_LABELS.getValue(field), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = { onSortChange(sort.copy(direction = SortDirection.ASCENDING)) },
                ) {
                    Text(if (sort.direction == SortDirection.ASCENDING) "● Ascending" else "Ascending")
                }
                TextButton(
                    onClick = { onSortChange(sort.copy(direction = SortDirection.DESCENDING)) },
                ) {
                    Text(if (sort.direction == SortDirection.DESCENDING) "● Descending" else "Descending")
                }
            }
        }
    }
}

/** Hosts both bottom sheets `ClosetScreen` can show — a small, standalone
 * extraction so `ClosetScreen`'s own body stays under detekt's `LongMethod`
 * threshold. */
@Composable
internal fun ClosetScreenSheets(
    state: ClosetUiState,
    viewModel: ClosetViewModel,
    isFilterSheetOpen: Boolean,
    onDismissFilterSheet: () -> Unit,
    isSortSheetOpen: Boolean,
    onDismissSortSheet: () -> Unit,
) {
    if (isFilterSheetOpen) {
        ClosetFilterSheet(
            filters = state.filters,
            options = state.filterOptions,
            onFiltersChange = viewModel::onFiltersChange,
            onClearAll = viewModel::onClearFilters,
            onDismiss = onDismissFilterSheet,
        )
    }

    if (isSortSheetOpen) {
        ClosetSortSheet(
            sort = state.sort,
            onSortChange = viewModel::onSortChange,
            onDismiss = onDismissSortSheet,
        )
    }
}

/** The two one-shot [LaunchedEffect]s [ClosetScreen] needs, extracted purely
 * to keep its own body under detekt's `LongMethod` threshold. */
@Composable
internal fun ClosetScreenEffects(
    state: ClosetUiState,
    viewModel: ClosetViewModel,
    toastState: ConfirmationToastState,
    initialFavoriteFilter: Boolean,
) {
    LaunchedEffect(Unit) {
        if (initialFavoriteFilter) viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(favoriteOnly = true))
    }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastState.show(it)
            viewModel.onToastShown()
        }
    }
}

@Composable
internal fun ClosetAddToWardrobeOverlay(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    addActions: ClosetAddActions,
) {
    if (isOpen) {
        AddToWardrobeSheet(
            onDismiss = onDismiss,
            onTakePhoto = addActions.onTakePhoto,
            onImportStarted = addActions.onImportStarted,
        )
    }
}
