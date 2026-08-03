package com.wardrobe.app.feature.closet.common

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

/** Every screen in this module renders a [Garment] the same way — one place to
 * resolve the category/brand names a tile subtitle needs (both are ids on the
 * domain model, not resolved objects, unlike palette/materials — see
 * `phase-5a-data-layer.md`'s mapping strategy for why). */
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
        needsReview = !isReviewed,
    )
}
