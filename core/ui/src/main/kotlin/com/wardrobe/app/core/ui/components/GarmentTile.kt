package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import com.wardrobe.app.core.ui.debug.RecompositionTracker

/**
 * `docs/design/component-library.md`'s "Garment Tile" — the core unit of the
 * whole browsing experience. Supports the resting/pressed/selected states the
 * spec describes; the "dragging" state belongs to the Outfit Builder
 * (Phase 5d), not this phase.
 *
 * Takes seven independent parameters because a grid tile genuinely has seven
 * independent inputs (data, two view states, three callbacks, layout modifier)
 * — bundling them into a wrapper object would trade an honest signature for a
 * layer of indirection with no behavior of its own.
 */
@Suppress("LongParameterList")
@Composable
fun GarmentTile(
    garment: GarmentTileUiModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecompositionTracker.track("GarmentTile")
    val tileShape = RoundedCornerShape(WardrobeRadius.tile)
    val thumbnailShape = RoundedCornerShape(WardrobeRadius.thumbnailInset)
    val selectionSuffix = if (isSelected) ", selected" else ""
    val tileDescription = "${garment.title}${garment.subtitle?.let { ", $it" }.orEmpty()}$selectionSuffix"
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = tileDescription
                    role = Role.Button
                }.wardrobeShadow(WardrobeElevation.RESTING, tileShape)
                .clip(tileShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor, shape = tileShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surface,
        shape = tileShape,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                GarmentTileThumbnail(garment.thumbnailPath, thumbnailShape)
                GarmentTileOverlay(garment, isSelected, isSelectionMode, onFavoriteClick)
            }
            GarmentTileLabels(garment.title, garment.subtitle, garment.needsReview)
        }
    }
}

@Composable
private fun GarmentTileThumbnail(
    thumbnailPath: String?,
    thumbnailShape: Shape,
) {
    if (thumbnailPath != null) {
        AsyncImage(
            model = thumbnailPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .clip(thumbnailShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .clip(thumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Checkroom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoxScope.GarmentTileOverlay(
    garment: GarmentTileUiModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onFavoriteClick: () -> Unit,
) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    } else if (!isSelectionMode) {
        val favoriteDescription =
            if (garment.isFavorite) {
                "Remove ${garment.title} from favorites"
            } else {
                "Favorite ${garment.title}"
            }
        IconButton(
            onClick = onFavoriteClick,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(FAVORITE_TOUCH_TARGET)
                    .semantics { contentDescription = favoriteDescription },
        ) {
            Icon(
                imageVector = if (garment.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint =
                    if (garment.isFavorite) {
                        WardrobeTheme.extendedColors.accent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

@Composable
private fun GarmentTileLabels(
    title: String,
    subtitle: String?,
    needsReview: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (needsReview) {
            NeedsReviewBadge(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private val FAVORITE_TOUCH_TARGET = 48.dp
