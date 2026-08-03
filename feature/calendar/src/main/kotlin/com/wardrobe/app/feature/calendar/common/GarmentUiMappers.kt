package com.wardrobe.app.feature.calendar.common

import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

/** Same mapping `feature:closet`/`feature:outfits` each keep their own copy
 * of — feature modules don't depend on each other (ADR-010). Calendar only
 * ever shows this in the "Log what you wore" picker, so it skips the
 * recently-added/favorite fields those other copies compute. */
fun Garment.toTileUiModel(): GarmentTileUiModel =
    GarmentTileUiModel(
        id = id.value,
        thumbnailPath = images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath,
        title = name?.takeUnless { it.isBlank() } ?: "Untitled item",
        subtitle = null,
        isFavorite = isFavorite,
        isRecentlyAdded = false,
    )
