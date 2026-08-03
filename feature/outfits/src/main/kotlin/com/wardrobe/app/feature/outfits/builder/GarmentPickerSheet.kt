package com.wardrobe.app.feature.outfits.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTile
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

private const val GRID_COLUMNS = 3

/** The tap-to-fill/tap-to-replace path — the mandatory non-drag equivalent
 * `component-library.md`'s Drag Handle/Layering Slot spec calls for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentPickerSheet(
    slot: OutfitSlot,
    garments: List<GarmentTileUiModel>,
    onSelect: (GarmentTileUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Choose ${slot.displayLabel()}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(garments, key = { it.id }) { garment ->
                GarmentTile(
                    garment = garment,
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = { onSelect(garment) },
                    onLongClick = {},
                    onFavoriteClick = {},
                )
            }
        }
    }
}
