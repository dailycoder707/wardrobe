package com.wardrobe.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius

/** `docs/design/component-library.md`'s "Loading State" — a warm-toned shimmer
 * (`surface` → `border`/`outline`), shaped like the content that's coming,
 * never a generic centered spinner. */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = SHIMMER_TRAVEL,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerTranslate",
        )
    val base = MaterialTheme.colorScheme.surface
    val highlight = MaterialTheme.colorScheme.outline
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - SHIMMER_WIDTH, 0f),
        end = Offset(translate, 0f),
    )
}

@Composable
fun GarmentTileSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(modifier = modifier.fillMaxWidth().background(brush, RoundedCornerShape(WardrobeRadius.tile))) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(4.dp)
                        .background(brush, RoundedCornerShape(WardrobeRadius.thumbnailInset)),
            )
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(brush, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** A grid-shaped skeleton matching the Closet grid's own column count, so the
 * loading state doesn't visually "jump" once real content arrives. */
@Composable
fun ClosetGridSkeleton(
    columns: Int,
    modifier: Modifier = Modifier,
    itemCount: Int = 12,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(itemCount) { GarmentTileSkeleton() }
    }
}

private const val SHIMMER_DURATION_MS = 1200
private const val SHIMMER_TRAVEL = 1000f
private const val SHIMMER_WIDTH = 300f
