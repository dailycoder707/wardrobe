package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.OutfitSlot

/** The nine curated capsule wardrobes the brief asks for — each maps to its
 * own season/dress-code/weather-bias preset in
 * `core:data`'s `CapsuleGenerator`. */
enum class CapsuleType {
    OFFICE,
    TRAVEL,
    WEEKEND,
    WEDDING,
    CASUAL,
    MINIMAL_PACKING,
    RAINY_WEATHER,
    COLD_WEATHER,
    HOT_SUMMER,
}

/**
 * A small, curated, mix-and-match set of the user's own existing garments for
 * one [CapsuleType] — deliberately not a single full daily outfit (a capsule
 * is interchangeable pieces, not one look), and never an item the wardrobe
 * doesn't actually contain.
 */
data class CapsuleSuggestion(
    val type: CapsuleType,
    val itemsBySlot: Map<OutfitSlot, List<GarmentId>>,
    val explanation: String,
)
