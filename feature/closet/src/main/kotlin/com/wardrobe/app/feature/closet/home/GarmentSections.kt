package com.wardrobe.app.feature.closet.home

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import com.wardrobe.app.feature.closet.common.toTileUiModel

private const val RECENT_SECTION_LIMIT = 10

internal data class GarmentSections(
    val recentlyAdded: List<GarmentTileUiModel>,
    val recentlyWorn: List<GarmentTileUiModel>,
    val continueEditing: List<GarmentTileUiModel>,
)

/** A top-level function (not a [HomeViewModel] member) purely to keep
 * `buildUiState`'s own body under detekt's `LongMethod` threshold — the
 * same "extract to a sibling file" convention [buildHomeInsights] already
 * established. */
internal fun buildGarmentSections(
    activeGarments: List<Garment>,
    wearEvents: List<WearEvent>,
    garmentsById: Map<GarmentId, Garment>,
    categoriesById: Map<CategoryId, Category>,
    brandsById: Map<BrandId, Brand>,
): GarmentSections {
    val recentlyAdded =
        activeGarments
            .sortedByDescending { it.createdAt }
            .take(RECENT_SECTION_LIMIT)
            .map { it.toTileUiModel(categoriesById, brandsById) }

    val recentlyWorn =
        wearEvents
            .filter { it.garmentId != null }
            .sortedByDescending { it.date }
            .mapNotNull { it.garmentId }
            .distinct()
            .take(RECENT_SECTION_LIMIT)
            .mapNotNull { id -> garmentsById[id]?.toTileUiModel(categoriesById, brandsById) }

    val continueEditing =
        activeGarments
            .filter { !it.isReviewed }
            .sortedByDescending { it.updatedAt }
            .take(RECENT_SECTION_LIMIT)
            .map { it.toTileUiModel(categoriesById, brandsById) }

    return GarmentSections(recentlyAdded, recentlyWorn, continueEditing)
}
