package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.ui.components.GarmentTile
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import androidx.compose.foundation.lazy.grid.items as gridItems

private const val GRID_COLUMNS = 3

/** Calendar's "Log what you wore" — pick a saved look, or pick individual
 * garments directly with no forced outfit-wrapping (`docs/design/
 * screen-specifications.md` §10, an explicit fix over the source-app
 * teardown's forced-outfit logging). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWearSheet(
    savedOutfits: List<SimpleOutfitOption>,
    closetGarments: List<GarmentTileUiModel>,
    onSelectOutfit: (Long) -> Unit,
    onSelectGarment: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            if (savedOutfits.isNotEmpty()) {
                Text(
                    "Saved looks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(savedOutfits, key = { it.id }) { outfit ->
                        OutfitOptionTile(outfit = outfit, onClick = { onSelectOutfit(outfit.id) })
                    }
                }
            }

            Text(
                "Individual items",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(closetGarments, key = { it.id }) { garment ->
                    GarmentTile(
                        garment = garment,
                        isSelected = false,
                        isSelectionMode = false,
                        onClick = { onSelectGarment(garment.id) },
                        onLongClick = {},
                        onFavoriteClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitOptionTile(
    outfit: SimpleOutfitOption,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(96.dp),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().size(72.dp)) {
                if (outfit.thumbnailPath != null) {
                    AsyncImage(
                        model = outfit.thumbnailPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(imageVector = Icons.Outlined.Checkroom, contentDescription = null)
                }
            }
            Text(
                text = outfit.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
