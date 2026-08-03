package com.wardrobe.app.feature.outfits.recommendations

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.feature.outfits.common.toTileUiModel

fun ScoredOutfit.toUiModel(
    garmentsById: Map<GarmentId, Garment>,
    categoriesById: Map<CategoryId, Category>,
    brandsById: Map<BrandId, Brand>,
): RecommendedOutfitUiModel {
    val items =
        outfit.garments.mapNotNull { slotEntry ->
            val garment = garmentsById[slotEntry.garmentId] ?: return@mapNotNull null
            val slot = OutfitSlot.fromIndex(slotEntry.layerSlot)
            RecommendedItemUiModel(
                slot = slot,
                slotLabel = slot.naturalLabel(),
                tile = garment.toTileUiModel(categoriesById, brandsById),
            )
        }
    val accessoryUiItems =
        accessoryItems.mapNotNull { entry ->
            val garment = garmentsById[entry.garmentId] ?: return@mapNotNull null
            RecommendedAccessoryUiModel(
                label =
                    entry.category.name
                        .lowercase()
                        .replace('_', ' ')
                        .replaceFirstChar(Char::uppercase),
                garmentName = garment.name ?: "Untitled item",
                explanation = entry.explanation,
            )
        }
    val jewelryUiItems =
        jewelryItems.mapNotNull { entry ->
            val garment = garmentsById[entry.garmentId] ?: return@mapNotNull null
            RecommendedAccessoryUiModel(
                label =
                    entry.category.name
                        .lowercase()
                        .replace('_', ' ')
                        .replaceFirstChar(Char::uppercase),
                garmentName = garment.name ?: "Untitled item",
                explanation = entry.explanation,
            )
        }
    return RecommendedOutfitUiModel(
        outfit = outfit,
        explanation = explanation,
        score = score,
        items = items,
        accessoryItems = accessoryUiItems,
        jewelryItems = jewelryUiItems,
    )
}

fun OutfitSlot.naturalLabel(): String =
    when (this) {
        OutfitSlot.TOP -> "Top"
        OutfitSlot.BOTTOM -> "Bottom"
        OutfitSlot.DRESS -> "Dress"
        OutfitSlot.OUTERWEAR -> "Outerwear"
        OutfitSlot.SHOES -> "Shoes"
        OutfitSlot.BAG -> "Bag"
        OutfitSlot.JEWELRY -> "Jewelry"
        OutfitSlot.WATCH -> "Watch"
        OutfitSlot.ACCESSORIES -> "Accessory"
    }
