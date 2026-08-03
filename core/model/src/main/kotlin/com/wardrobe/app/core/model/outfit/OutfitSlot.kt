package com.wardrobe.app.core.model.outfit

/**
 * The named-slot mapping for [OutfitGarmentSlot.layerSlot]'s persisted `Int` —
 * the Outfit Builder (Phase 5d) presents these nine slots, in dressing order,
 * as [slotIndex] (this enum's declaration order via [ordinal], not a separate
 * hand-maintained numbering — one less place for the two to drift apart).
 * [DRESS] is mutually exclusive with [TOP]/[BOTTOM] at the UI layer (the
 * builder ViewModel's job, not this enum's), the same way a real outfit is
 * either a dress or a top-and-bottom, never both.
 */
enum class OutfitSlot {
    TOP,
    BOTTOM,
    DRESS,
    OUTERWEAR,
    SHOES,
    BAG,
    JEWELRY,
    WATCH,
    ACCESSORIES,
    ;

    val slotIndex: Int get() = ordinal

    companion object {
        fun fromIndex(slotIndex: Int): OutfitSlot = entries.first { it.slotIndex == slotIndex }

        /**
         * A keyword heuristic against a garment's own category name — added
         * Phase 6 so the styling engine can bucket candidate garments by slot
         * without a hardcoded item-type taxonomy (this schema's `Category` tree
         * is free-form and user-editable, phase-1-architecture.md Section 9).
         * Tuned to [com.wardrobe.app.core.database.WardrobeDatabase.SeedCallback]'s
         * default category names but falls back gracefully — an unrecognized
         * category name yields `null` rather than a wrong guess, and the engine
         * simply can't place that garment automatically.
         */
        private val DRESS_KEYWORDS = listOf("dress", "jumpsuit", "saree", "sari")
        private val TOP_KEYWORDS = listOf("top", "shirt", "blouse", "sweater", "polo", "hood", "kurta")
        private val BOTTOM_KEYWORDS = listOf("bottom", "pant", "skirt", "short", "jean", "legging")
        private val OUTERWEAR_KEYWORDS = listOf("outerwear", "jacket", "coat", "blazer")
        private val SHOES_KEYWORDS =
            listOf("shoe", "sandal", "boot", "sneaker", "heel", "flat", "loafer", "slipper")
        private val BAG_KEYWORDS = listOf("bag", "purse", "handbag", "backpack", "tote", "wallet")
        private val WATCH_KEYWORDS = listOf("watch")
        private val JEWELRY_KEYWORDS =
            listOf("jewel", "earring", "necklace", "bracelet", "ring", "anklet")
        private val ACCESSORIES_KEYWORDS =
            listOf("belt", "scar", "hair", "sunglass", "accessor", "cap", "hat", "glove", "sock", "tie")

        fun classify(categoryName: String): OutfitSlot? {
            val lower = categoryName.lowercase()
            return when {
                DRESS_KEYWORDS.any { it in lower } -> DRESS

                // Checked before TOP/BOTTOM/OUTERWEAR/SHOES: "Laptop Bag" contains "top" as a
                // raw substring, so a bag-specific match must win before the broader keyword lists run.
                BAG_KEYWORDS.any { it in lower } -> BAG

                TOP_KEYWORDS.any { it in lower } -> TOP

                BOTTOM_KEYWORDS.any { it in lower } -> BOTTOM

                OUTERWEAR_KEYWORDS.any { it in lower } -> OUTERWEAR

                SHOES_KEYWORDS.any { it in lower } -> SHOES

                WATCH_KEYWORDS.any { it in lower } -> WATCH

                JEWELRY_KEYWORDS.any { it in lower } -> JEWELRY

                ACCESSORIES_KEYWORDS.any { it in lower } -> ACCESSORIES

                else -> null
            }
        }
    }
}
