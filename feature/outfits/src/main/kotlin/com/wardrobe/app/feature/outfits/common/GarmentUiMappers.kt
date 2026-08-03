package com.wardrobe.app.feature.outfits.common

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val RECENTLY_ADDED_WITHIN_DAYS = 7L

/** Same mapping `feature:closet`'s own `GarmentUiMappers.kt` uses — duplicated
 * rather than shared, since feature modules don't depend on each other
 * (ADR-010) and this is a small, stable mapping. */
fun Garment.toTileUiModel(
    categoriesById: Map<CategoryId, Category>,
    brandsById: Map<BrandId, Brand>,
    now: Instant = Instant.now(),
): GarmentTileUiModel {
    val categoryName = categoriesById[categoryId]?.name
    val brandName = brandId?.let { brandsById[it]?.name }
    return GarmentTileUiModel(
        id = id.value,
        thumbnailPath = images.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath,
        title = name?.takeUnless { it.isBlank() } ?: categoryName ?: "Untitled item",
        subtitle = brandName,
        isFavorite = isFavorite,
        isRecentlyAdded = ChronoUnit.DAYS.between(createdAt, now) <= RECENTLY_ADDED_WITHIN_DAYS,
    )
}
