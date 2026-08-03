package com.wardrobe.app.feature.outfits.list

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
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

private val FAVORITE_TOUCH_TARGET = 48.dp
private const val MOSAIC_FOURTH_INDEX = 3

/** `docs/design/screen-specifications.md` §9's "Outfit Card" — a flat-lay
 * mosaic of the outfit's constituent garments, not one photo. No dedicated
 * component-library.md entry exists for this (only described inline in the
 * screen spec), so it lives here rather than `core:ui`. */
@Composable
fun OutfitCard(
    outfit: OutfitCardUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onTryOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WardrobeRadius.tile)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = outfit.title }
                .wardrobeShadow(WardrobeElevation.RESTING, shape)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                OutfitMosaic(thumbnailPaths = outfit.thumbnailPaths, modifier = Modifier.fillMaxSize())
                OutfitFavoriteButton(outfit = outfit, onFavoriteClick = onFavoriteClick)
                OutfitTryOnButton(outfit = outfit, onTryOnClick = onTryOnClick)
            }
            OutfitCardLabels(outfit)
        }
    }
}

@Composable
private fun BoxScope.OutfitTryOnButton(
    outfit: OutfitCardUiModel,
    onTryOnClick: () -> Unit,
) {
    IconButton(
        onClick = onTryOnClick,
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .size(FAVORITE_TOUCH_TARGET)
                .semantics {
                    contentDescription = "Try on ${outfit.title}"
                    role = Role.Button
                },
    ) {
        Icon(imageVector = Icons.Filled.Checkroom, contentDescription = null)
    }
}

@Composable
private fun BoxScope.OutfitFavoriteButton(
    outfit: OutfitCardUiModel,
    onFavoriteClick: () -> Unit,
) {
    val favoriteDescription =
        if (outfit.isFavorite) "Remove ${outfit.title} from favorites" else "Favorite ${outfit.title}"
    val favoriteTint =
        if (outfit.isFavorite) WardrobeTheme.extendedColors.accent else MaterialTheme.colorScheme.onSurface

    IconButton(
        onClick = onFavoriteClick,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(FAVORITE_TOUCH_TARGET)
                .semantics {
                    contentDescription = favoriteDescription
                    role = Role.Button
                },
    ) {
        Icon(
            imageVector = if (outfit.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = favoriteTint,
        )
    }
}

@Composable
private fun OutfitCardLabels(outfit: OutfitCardUiModel) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            text = outfit.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (outfit.occasionName != null) {
            Text(
                text = outfit.occasionName,
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A 2×2 flat-lay collage for 2–4 garments, a single crop for exactly one,
 * and a placeholder icon for an outfit with no resolvable thumbnails yet. */
@Composable
private fun OutfitMosaic(
    thumbnailPaths: List<String>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        when {
            thumbnailPaths.isEmpty() -> {
                Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            thumbnailPaths.size == 1 -> {
                MosaicImage(thumbnailPaths[0], Modifier.fillMaxSize())
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MosaicImage(thumbnailPaths[0], Modifier.weight(1f).fillMaxSize())
                        MosaicImage(thumbnailPaths.getOrNull(1), Modifier.weight(1f).fillMaxSize())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MosaicImage(thumbnailPaths.getOrNull(2), Modifier.weight(1f).fillMaxSize())
                        MosaicImage(thumbnailPaths.getOrNull(MOSAIC_FOURTH_INDEX), Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun MosaicImage(
    path: String?,
    modifier: Modifier = Modifier,
) {
    if (path == null) return
    AsyncImage(
        model = path,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
