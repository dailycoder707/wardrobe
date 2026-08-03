package com.wardrobe.app.core.model.styling

/**
 * A finer classification than [com.wardrobe.app.core.model.outfit.OutfitSlot.JEWELRY]
 * — that slot stays a single bucket in the Outfit Builder's persisted, one-item-
 * per-slot canvas (Phase 5d; `outfit_garments`' primary key is `(outfitId, layerSlot)`,
 * so it can only ever hold one garment per slot). Phase 9's recommendation engine
 * needs to reason about — and display — a necklace, a bracelet, and a ring all at
 * once without a schema change, so this enum exists purely for that presentation-only
 * breakdown, the same "finer classification, same free-form Category taxonomy"
 * relationship [AccessoryCategory] already has to [OutfitSlot.ACCESSORIES].
 */
enum class JewelryCategory {
    NECKLACE,
    BRACELET,
    RING,
    EARRING,
    OTHER,
    ;

    companion object {
        private val NECKLACE_KEYWORDS = listOf("necklace", "pendant", "chain")
        private val BRACELET_KEYWORDS = listOf("bracelet", "bangle")
        private val RING_KEYWORDS = listOf("ring")
        private val EARRING_KEYWORDS = listOf("earring", "stud", "hoop")

        fun classify(categoryName: String): JewelryCategory {
            val lower = categoryName.lowercase()
            return when {
                NECKLACE_KEYWORDS.any { it in lower } -> NECKLACE
                BRACELET_KEYWORDS.any { it in lower } -> BRACELET
                RING_KEYWORDS.any { it in lower } -> RING
                EARRING_KEYWORDS.any { it in lower } -> EARRING
                else -> OTHER
            }
        }
    }
}
