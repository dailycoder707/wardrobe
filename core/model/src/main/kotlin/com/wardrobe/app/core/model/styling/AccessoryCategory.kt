package com.wardrobe.app.core.model.styling

/**
 * A finer classification than [com.wardrobe.app.core.model.outfit.OutfitSlot.ACCESSORIES]
 * — that slot is a deliberate catch-all in the Outfit Builder (Phase 5d), which this
 * enum does not change. The styling engine (Phase 6) needs to reason about individual
 * accessory types for its "Include Belt"/"Include Hair Accessories"/"Include
 * Sunglasses" preferences and completeness checks, but this app's `Category` taxonomy
 * is a free-form, user-editable tree with no fixed vocabulary (phase-1-architecture.md
 * Section 9) — so [classify] is a keyword heuristic against a category's own name,
 * the same kind of "good enough, explicitly labeled as a heuristic" approach
 * `ColorHarmony` already uses, not a guarantee.
 */
enum class AccessoryCategory {
    BELT,
    HAIR_ACCESSORY,
    SUNGLASSES,
    SCARF,
    OTHER,
    ;

    companion object {
        private val BELT_KEYWORDS = listOf("belt")
        private val HAIR_KEYWORDS = listOf("hair", "clip", "scrunchie", "headband")
        private val SUNGLASSES_KEYWORDS = listOf("sunglass", "shades")
        private val SCARF_KEYWORDS = listOf("scarf", "shawl", "wrap")

        fun classify(categoryName: String): AccessoryCategory {
            val lower = categoryName.lowercase()
            return when {
                BELT_KEYWORDS.any { it in lower } -> BELT
                HAIR_KEYWORDS.any { it in lower } -> HAIR_ACCESSORY
                SUNGLASSES_KEYWORDS.any { it in lower } -> SUNGLASSES
                SCARF_KEYWORDS.any { it in lower } -> SCARF
                else -> OTHER
            }
        }
    }
}
