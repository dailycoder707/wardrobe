package com.wardrobe.app.core.ui.components

import androidx.compose.runtime.Immutable

/** The plain-data shape [GarmentTile] renders — deliberately not the full
 * domain `Garment` (which carries far more than a grid tile needs and would
 * make every tile recompose on any field change). `feature:closet` maps its
 * `Garment` + wear-stat data into this once per list emission. */
@Immutable
data class GarmentTileUiModel(
    val id: Long,
    val thumbnailPath: String?,
    val title: String,
    val subtitle: String?,
    val isFavorite: Boolean,
    val isRecentlyAdded: Boolean,
    val needsReview: Boolean = false,
)
