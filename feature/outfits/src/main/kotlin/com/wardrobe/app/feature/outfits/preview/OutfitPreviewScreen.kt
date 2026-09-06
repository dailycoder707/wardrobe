package com.wardrobe.app.feature.outfits.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.outfit.OutfitSlot

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 3f
private val MANNEQUIN_STROKE_WIDTH = 3.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitPreviewScreen(
    modifier: Modifier = Modifier,
    viewModel: OutfitPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Outfit Preview") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                PannableZoomableMannequin(state.layers)
            }
        }
    }
}

@Composable
private fun PannableZoomableMannequin(layers: List<PreviewLayerUiModel>) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        offset += pan
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
        ) {
            MannequinSilhouette()
            layers.forEach { layer -> LayeredGarment(layer) }
        }
    }
}

@Composable
private fun MannequinSilhouette() {
    val lineColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        val strokeWidthPx = MANNEQUIN_STROKE_WIDTH.toPx()
        val centerX = size.width / 2f
        drawCircle(
            color = lineColor,
            radius = size.width * HEAD_RADIUS_FRACTION,
            center =
                Offset(
                    centerX,
                    size.height * HEAD_Y_FRACTION,
                ),
        )
        drawLine(
            color = lineColor,
            start = Offset(centerX, size.height * TORSO_TOP_FRACTION),
            end = Offset(centerX, size.height * LEGS_BOTTOM_FRACTION),
            strokeWidth = strokeWidthPx,
        )
    }
}

private const val HEAD_RADIUS_FRACTION = 0.05f
private const val HEAD_Y_FRACTION = 0.08f
private const val TORSO_TOP_FRACTION = 0.14f
private const val LEGS_BOTTOM_FRACTION = 0.9f

@Composable
private fun LayeredGarment(layer: PreviewLayerUiModel) {
    val yOffset = slotVerticalOffset(layer.slot)
    AsyncImage(
        model = layer.imagePath,
        contentDescription = layer.title,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .offset(y = yOffset)
                .fillMaxWidth(),
    )
}

private fun slotVerticalOffset(slot: OutfitSlot): Dp =
    when (slot) {
        OutfitSlot.TOP, OutfitSlot.DRESS, OutfitSlot.OUTERWEAR -> 60.dp
        OutfitSlot.BOTTOM -> 160.dp
        OutfitSlot.SHOES -> 320.dp
        OutfitSlot.BAG -> 100.dp
        OutfitSlot.JEWELRY, OutfitSlot.WATCH, OutfitSlot.ACCESSORIES -> 40.dp
    }
