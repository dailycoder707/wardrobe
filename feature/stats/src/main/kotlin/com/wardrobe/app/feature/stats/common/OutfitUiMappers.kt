package com.wardrobe.app.feature.stats.common

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.outfit.Outfit

/** An outfit has no photo of its own yet (Phase 5d's `photoUri` is reserved
 * for a future flat-lay capture) — every screen that previews a saved look
 * resolves its thumbnail from its first-by-layer garment's own thumbnail,
 * the same fallback `feature:outfits`/`feature:calendar` already use. */
fun Outfit.toInsightItem(
    metric: String,
    garmentsById: Map<GarmentId, Garment>,
): InsightItemUiModel {
    val thumbnailPath =
        garments.sortedBy { it.layerSlot }.firstNotNullOfOrNull { slot ->
            garmentsById[slot.garmentId]?.images?.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
        }
    return InsightItemUiModel(
        id = id.value,
        isOutfit = true,
        thumbnailPath = thumbnailPath,
        title = name?.takeUnless { it.isBlank() } ?: "Untitled look",
        metric = metric,
    )
}
