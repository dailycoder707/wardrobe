package com.wardrobe.app.feature.capture.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.model.garment.ComparisonStage
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 4f
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f

private enum class PrimaryImageTab(
    val label: String,
    val imageType: ImageType,
) {
    ORIGINAL("Original", ImageType.ORIGINAL),
    CUTOUT("Transparent Cutout", ImageType.CUTOUT),
    WHITE_BACKGROUND("White Background", ImageType.WHITE_BACKGROUND),
}

/**
 * M10's premium image viewer (acceptance criteria §1/§4): a segmented
 * Original/Transparent Cutout/White Background control backed by
 * pinch-to-zoom-and-pan (double-tap also toggles zoom), plus — only when
 * more than one AI stage actually ran — a "what did AI change" comparison
 * strip built from [comparisonStages]. A garment whose extraction failed
 * has only an ORIGINAL comparison stage, so the strip simply doesn't
 * render rather than showing a single, pointless thumbnail.
 */
@Composable
internal fun GarmentReviewImageViewer(
    variants: List<ImageVariant>,
    comparisonStages: List<ComparisonStage>,
    modifier: Modifier = Modifier,
) {
    val pathByType = remember(variants) { variants.associateBy { it.type } }
    val availableTabs = remember(pathByType) { PrimaryImageTab.entries.filter { pathByType.containsKey(it.imageType) } }
    var selectedTab by remember(availableTabs) {
        mutableStateOf(availableTabs.firstOrNull { it == PrimaryImageTab.CUTOUT } ?: availableTabs.firstOrNull())
    }
    var comparisonOverridePath by remember(variants) { mutableStateOf<String?>(null) }

    val currentPath = comparisonOverridePath ?: selectedTab?.let { pathByType[it.imageType]?.filePath }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Crossfade(targetState = currentPath, label = "reviewImage") { path ->
            ZoomableImage(
                model = path,
                contentDescription = "Garment photo",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )
        }
        if (availableTabs.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTabs.forEach { tab ->
                    WardrobeFilterChip(
                        label = tab.label,
                        selected = comparisonOverridePath == null && selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            comparisonOverridePath = null
                        },
                    )
                }
            }
        }
        AnimatedVisibility(visible = comparisonStages.size > 1) {
            ComparisonStrip(
                stages = comparisonStages,
                selectedPath = comparisonOverridePath,
                onSelect = { stage -> comparisonOverridePath = stage.filePath },
            )
        }
    }
}

@Composable
private fun ComparisonStrip(
    stages: List<ComparisonStage>,
    selectedPath: String?,
    onSelect: (ComparisonStage) -> Unit,
) {
    Column {
        Text("What AI changed", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            items(stages, key = { it.label }) { stage ->
                ComparisonThumbnail(
                    stage = stage,
                    selected = stage.filePath == selectedPath,
                    onClick = { onSelect(stage) },
                )
            }
        }
    }
}

@Composable
private fun ComparisonThumbnail(
    stage: ComparisonStage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(72.dp)) {
        val shape = RoundedCornerShape(12.dp)
        AsyncImage(
            model = stage.filePath,
            contentDescription = stage.label.displayName(),
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(shape)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clickable(onClick = onClick),
        )
        Text(
            stage.label.displayName(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ZoomableImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.clipToBounds()) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ).pointerInput(model) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                            scale = newScale
                            offset = if (newScale <= MIN_ZOOM_SCALE) Offset.Zero else offset + pan
                        }
                    }.pointerInput(model) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > MIN_ZOOM_SCALE) {
                                    scale = MIN_ZOOM_SCALE
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_ZOOM_SCALE
                                }
                            },
                        )
                    },
        )
    }
}

private fun ComparisonStageLabel.displayName(): String =
    when (this) {
        ComparisonStageLabel.ORIGINAL -> "Original"
        ComparisonStageLabel.EXTRACTED -> "Extracted"
        ComparisonStageLabel.ENHANCED -> "Enhanced"
        ComparisonStageLabel.RECONSTRUCTED -> "Reconstructed"
    }
