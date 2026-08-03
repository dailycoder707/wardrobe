package com.wardrobe.app.feature.outfits.builder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.ConfirmationToastState
import com.wardrobe.app.core.ui.components.GarmentTile
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import com.wardrobe.app.core.ui.components.rememberConfirmationToastState

private const val GRID_COLUMNS = 3

/** Bundles the canvas/browser interaction callbacks so [OutfitBuilderBody] and
 * [OutfitCanvasSection] stay under detekt's LongParameterList threshold —
 * the same grouping pattern used for Calendar's `DayDetailActions`. */
private data class OutfitBuilderInteractions(
    val onSlotTap: (OutfitSlot) -> Unit,
    val onSlotLongPress: (OutfitSlot) -> Unit,
    val onSlotPositioned: (OutfitSlot, Rect) -> Unit,
    val onQuickAdd: (GarmentTileUiModel) -> Unit,
    val onDragStart: (GarmentTileUiModel, Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onClearOutfit: () -> Unit,
    val onOpenDetails: () -> Unit,
)

private data class OutfitBuilderTopBarState(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isSaving: Boolean,
)

/** Groups the drag ghost's visual state so [OutfitBuilderContent] stays under
 * detekt's LongParameterList threshold. */
private data class DragVisualState(
    val draggingOverSlot: OutfitSlot?,
    val draggedGarment: GarmentTileUiModel?,
    val dragPosition: Offset,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitBuilderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutfitBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerSlot by remember { mutableStateOf<OutfitSlot?>(null) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    val toastState = rememberConfirmationToastState()

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastState.show(it)
            viewModel.onToastShown()
        }
    }
    LaunchedEffect(state.didSave) {
        if (state.didSave) onBack()
    }

    val (interactions, dragVisual) =
        rememberBuilderInteractionState(
            state = state,
            viewModel = viewModel,
            onOpenPicker = { slot -> pickerSlot = slot },
            onOpenDetails = { showMetadataSheet = true },
        )

    Scaffold(
        modifier = modifier,
        topBar = {
            OutfitBuilderTopBar(
                state = OutfitBuilderTopBarState(state.canUndo, state.canRedo, state.isSaving),
                onBack = onBack,
                onUndo = viewModel::onUndo,
                onRedo = viewModel::onRedo,
                onSave = viewModel::onSave,
            )
        },
    ) { innerPadding ->
        OutfitBuilderContent(
            state = state,
            toastState = toastState,
            dragVisual = dragVisual,
            interactions = interactions,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    OutfitBuilderOverlays(
        state = state,
        viewModel = viewModel,
        pickerSlot = pickerSlot,
        onPickerDismissed = { pickerSlot = null },
        showMetadataSheet = showMetadataSheet,
        onMetadataSheetDismissed = { showMetadataSheet = false },
    )
}

@Composable
private fun rememberBuilderInteractionState(
    state: OutfitBuilderUiState,
    viewModel: OutfitBuilderViewModel,
    onOpenPicker: (OutfitSlot) -> Unit,
    onOpenDetails: () -> Unit,
): Pair<OutfitBuilderInteractions, DragVisualState> {
    var slotRects by remember { mutableStateOf(emptyMap<OutfitSlot, Rect>()) }
    var draggedGarment by remember { mutableStateOf<GarmentTileUiModel?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val dropSlot =
        if (draggedGarment == null) {
            null
        } else {
            slotRects.entries.firstOrNull { it.value.contains(dragPosition) }?.key
        }
    val interactions =
        OutfitBuilderInteractions(
            onSlotTap = onOpenPicker,
            onSlotLongPress = { slot -> if (state.slots.containsKey(slot)) viewModel.onRemoveGarment(slot) },
            onSlotPositioned = { slot, rect -> slotRects = slotRects + (slot to rect) },
            onQuickAdd = { garment -> viewModel.onQuickAddGarment(GarmentId(garment.id)) },
            onDragStart = { garment, position ->
                draggedGarment = garment
                dragPosition = position
            },
            onDrag = { delta -> dragPosition += delta },
            onDragEnd = {
                val garment = draggedGarment
                val slot = dropSlot
                if (garment != null && slot != null) {
                    viewModel.onPlaceGarment(slot, GarmentId(garment.id))
                }
                draggedGarment = null
            },
            onClearOutfit = viewModel::onClearOutfit,
            onOpenDetails = onOpenDetails,
        )
    return interactions to DragVisualState(dropSlot, draggedGarment, dragPosition)
}

@Composable
private fun OutfitBuilderContent(
    state: OutfitBuilderUiState,
    toastState: ConfirmationToastState,
    dragVisual: DragVisualState,
    interactions: OutfitBuilderInteractions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            OutfitBuilderBody(
                state = state,
                draggingOverSlot = dragVisual.draggingOverSlot,
                interactions = interactions,
            )
        }

        dragVisual.draggedGarment?.let { garment ->
            DraggedGarmentGhost(garment = garment, position = dragVisual.dragPosition)
        }

        ConfirmationToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun OutfitBuilderOverlays(
    state: OutfitBuilderUiState,
    viewModel: OutfitBuilderViewModel,
    pickerSlot: OutfitSlot?,
    onPickerDismissed: () -> Unit,
    showMetadataSheet: Boolean,
    onMetadataSheetDismissed: () -> Unit,
) {
    pickerSlot?.let { slot ->
        GarmentPickerSheet(
            slot = slot,
            garments = state.closetGarments,
            onSelect = { garment ->
                viewModel.onPlaceGarment(slot, GarmentId(garment.id))
                onPickerDismissed()
            },
            onDismiss = onPickerDismissed,
        )
    }

    if (showMetadataSheet) {
        OutfitMetadataSheet(
            form = state.form,
            reference = state.reference,
            onFormChange = viewModel::onFormChange,
            onDismiss = onMetadataSheetDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutfitBuilderTopBar(
    state: OutfitBuilderTopBarState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = { Text("Build a Look") },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel") }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = state.canUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = state.canRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "Redo")
            }
            Button(onClick = onSave, enabled = !state.isSaving) { Text("Save") }
        },
    )
}

@Composable
private fun OutfitBuilderBody(
    state: OutfitBuilderUiState,
    draggingOverSlot: OutfitSlot?,
    interactions: OutfitBuilderInteractions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                OutfitCanvasSection(
                    state = state,
                    draggingOverSlot = draggingOverSlot,
                    interactions = interactions,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                )
                ClosetBrowser(
                    garments = state.closetGarments,
                    interactions = interactions,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                OutfitCanvasSection(
                    state = state,
                    draggingOverSlot = draggingOverSlot,
                    interactions = interactions,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                ClosetBrowser(
                    garments = state.closetGarments,
                    interactions = interactions,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OutfitCanvasSection(
    state: OutfitBuilderUiState,
    draggingOverSlot: OutfitSlot?,
    interactions: OutfitBuilderInteractions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.colorHarmony?.label ?: "Add items to begin",
                style = MaterialTheme.typography.labelMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            Row {
                TextButton(onClick = interactions.onOpenDetails) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Details")
                }
                TextButton(onClick = interactions.onClearOutfit, enabled = !state.isEmpty) { Text("Clear") }
            }
        }
        OutfitCanvas(
            slots = state.slots,
            onSlotTap = interactions.onSlotTap,
            onSlotLongPress = interactions.onSlotLongPress,
            onSlotPositioned = interactions.onSlotPositioned,
            draggingOverSlot = draggingOverSlot,
        )
    }
}

@Composable
private fun ClosetBrowser(
    garments: List<GarmentTileUiModel>,
    interactions: OutfitBuilderInteractions,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(garments, key = { it.id }) { garment ->
            DraggableGarmentTile(
                garment = garment,
                onTap = { interactions.onQuickAdd(garment) },
                onDragStart = interactions.onDragStart,
                onDrag = interactions.onDrag,
                onDragEnd = interactions.onDragEnd,
            )
        }
    }
}

@Composable
private fun DraggableGarmentTile(
    garment: GarmentTileUiModel,
    onTap: () -> Unit,
    onDragStart: (GarmentTileUiModel, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates -> rootPosition = coordinates.positionInRoot() }
                .pointerInput(garment.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { localOffset -> onDragStart(garment, rootPosition + localOffset) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
    ) {
        GarmentTile(
            garment = garment,
            isSelected = false,
            isSelectionMode = false,
            onClick = onTap,
            onLongClick = {},
            onFavoriteClick = {},
        )
    }
}

/** The lifted, floating copy of the tile being dragged — `motion-guide.md`'s
 * "pick-up → scale 105% + lift to FLOATING elevation." Positioned in root
 * coordinates, matching how [OutfitCanvas]'s slot rects are measured. */
@Composable
private fun DraggedGarmentGhost(
    garment: GarmentTileUiModel,
    position: Offset,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            Modifier
                .offset { IntOffset(position.x.toInt(), position.y.toInt()) }
                .size(96.dp)
                .graphicsLayer(scaleX = 1.05f, scaleY = 1.05f)
                .wardrobeShadow(WardrobeElevation.FLOATING, shape),
    ) {
        GarmentTile(
            garment = garment,
            isSelected = false,
            isSelectionMode = false,
            onClick = {},
            onLongClick = {},
            onFavoriteClick = {},
        )
    }
}
