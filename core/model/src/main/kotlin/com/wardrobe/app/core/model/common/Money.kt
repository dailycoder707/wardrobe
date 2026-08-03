package com.wardrobe.app.core.model.common

/**
 * Used by both [com.wardrobe.app.core.model.garment.Garment] and
 * [com.wardrobe.app.core.model.wishlist.WishlistItem] — the second real use is what
 * justifies a shared type here rather than duplicating amount+currency on each model.
 * No FX conversion (Phase 1 Section 29: cost-per-wear is a personal number, not a
 * currency-converted one) — `currencyCode` is stored and displayed locale-aware,
 * never converted.
 */
data class Money(
    val amount: Double,
    val currencyCode: String,
)
