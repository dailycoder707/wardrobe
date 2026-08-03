package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.ui.components.GarmentTile
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import kotlin.math.roundToInt

private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6

/**
 * `docs/design/component-library.md`'s "Grid Density Control" — pinch-to-zoom
 * directly on the grid, snapping to whole column counts only (no mid-pinch
 * fractional state held at rest). The density stepper in the toolbar
 * ([ClosetScreen]) is the second, TalkBack-reachable path to the same result,
 * per that spec's accessibility requirement.
 */
@Suppress("LongParameterList")
@Composable
fun ClosetGrid(
    garments: List<GarmentTileUiModel>,
    columnCount: Int,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onColumnCountChange: (Int) -> Unit,
    onOpenGarment: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onFavoriteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var accumulatedScale by remember { mutableFloatStateOf(1f) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier =
            modifier.pointerInput(columnCount) {
                detectTransformGestures { _, _, zoom, _ ->
                    accumulatedScale *= zoom
                    // Snap to a whole column count only once the accumulated pinch
                    // crosses a full step, then reset — this is what keeps the grid
                    // from ever holding a "3.4 columns" fractional state at rest
                    // (component-library.md's Grid Density Control).
                    val delta =
                        if (accumulatedScale >
                            PINCH_STEP_THRESHOLD
                        ) {
                            -1
                        } else if (accumulatedScale < 1f / PINCH_STEP_THRESHOLD) {
                            1
                        } else {
                            0
                        }
                    if (delta != 0) {
                        onColumnCountChange((columnCount + delta).coerceIn(MIN_COLUMNS, MAX_COLUMNS))
                        accumulatedScale = 1f
                    }
                }
            },
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(garments, key = { it.id }) { garment ->
            GarmentTile(
                garment = garment,
                isSelected = garment.id in selectedIds,
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (isSelectionMode) onToggleSelection(garment.id) else onOpenGarment(garment.id)
                },
                onLongClick = {
                    if (!isSelectionMode) onEnterSelectionMode(garment.id) else onToggleSelection(garment.id)
                },
                onFavoriteClick = { onFavoriteClick(garment.id) },
            )
        }
    }
}

private const val PINCH_STEP_THRESHOLD = 1.35f
