package com.wardrobe.app.feature.tryon.render

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.tryon.rendering.TRY_ON_LAYER_WIDTH_FRACTION
import kotlin.math.roundToInt

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnScreen(
    onNeedsBodyProfile: () -> Unit,
    onEditMask: (GarmentId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TryOnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { CenterAlignedTopAppBar(title = { Text("Try On Me") }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.needsBodyProfile -> {
                    NeedsBodyProfilePrompt(onSetUp = onNeedsBodyProfile)
                }

                else -> {
                    TryOnCanvas(
                        state = state,
                        onLayerTransformed = viewModel::onLayerTransformed,
                        onResetToAutoPlacement = viewModel::onResetToAutoPlacement,
                        onEditMask = onEditMask,
                    )
                }
            }
        }
    }
}

@Composable
private fun NeedsBodyProfilePrompt(onSetUp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Set up your private try-on profile to see garments on your own photo — nothing leaves this device.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onSetUp) { Text("Set Up Try-On") }
    }
}

@Composable
internal fun TryOnCanvas(
    state: TryOnUiState,
    onLayerTransformed: (GarmentId, Float, Float, Float, Float) -> Unit,
    onResetToAutoPlacement: (GarmentId) -> Unit,
    onEditMask: (GarmentId) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()

        state.backgroundPhotoPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = "Your photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Sorted by `sortForRender` in the ViewModel — rendered in that order
        // so a later (higher-depth) layer draws on top of an earlier one.
        state.layers.forEach { layer ->
            key(layer.garmentId.value) {
                TryOnGarmentLayer(
                    layer,
                    canvasWidthPx,
                    canvasHeightPx,
                    onLayerTransformed,
                    onResetToAutoPlacement,
                    onEditMask,
                )
            }
        }
    }
}

@Composable
private fun TryOnGarmentLayer(
    layer: TryOnLayerUiModel,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onLayerTransformed: (GarmentId, Float, Float, Float, Float) -> Unit,
    onResetToAutoPlacement: (GarmentId) -> Unit,
    onEditMask: (GarmentId) -> Unit,
) {
    var offsetXFraction by remember(layer.template.id) { mutableFloatStateOf(layer.template.offsetXFraction) }
    var offsetYFraction by remember(layer.template.id) { mutableFloatStateOf(layer.template.offsetYFraction) }
    var scale by remember(layer.template.id) { mutableFloatStateOf(layer.template.scale) }
    var rotation by remember(layer.template.id) { mutableFloatStateOf(layer.template.rotationDegrees) }

    val density = LocalDensity.current
    val layerWidthPx = canvasWidthPx * TRY_ON_LAYER_WIDTH_FRACTION

    Column(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        (offsetXFraction * canvasWidthPx - layerWidthPx / 2f).roundToInt(),
                        (offsetYFraction * canvasHeightPx).roundToInt(),
                    )
                }.width(with(density) { layerWidthPx.toDp() })
                .semantics { contentDescription = layer.title },
    ) {
        if (layer.showConfidenceChip) {
            AssistChip(onClick = {}, label = { Text("Auto-placed — drag to adjust") })
        }
        AsyncImage(
            model = layer.imagePath,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation)
                    .pointerInput(layer.template.id) {
                        detectTransformGestures { _, pan, zoom, rotationDelta ->
                            offsetXFraction += pan.x / canvasWidthPx
                            offsetYFraction += pan.y / canvasHeightPx
                            scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            rotation += rotationDelta
                            onLayerTransformed(layer.garmentId, offsetXFraction, offsetYFraction, scale, rotation)
                        }
                    },
        )
        TextButton(onClick = { onResetToAutoPlacement(layer.garmentId) }) {
            Text("Reset to Auto Placement")
        }
        TextButton(onClick = { onEditMask(layer.garmentId) }) {
            Text("Edit Mask")
        }
    }
}
