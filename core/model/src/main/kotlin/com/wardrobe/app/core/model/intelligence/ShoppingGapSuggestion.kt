package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.CategoryId

/**
 * A comparative, non-prescriptive observation about wardrobe composition —
 * e.g. "You own 16 tops but only 2 handbags." Deliberately never phrased as
 * "you should buy N more X" (the brief: "avoid suggesting unnecessary
 * purchases"); [message] is always a comparison against the wardrobe's own
 * most-owned category, never an absolute external benchmark.
 */
data class ShoppingGapSuggestion(
    val categoryId: CategoryId,
    val categoryName: String,
    val ownedCount: Int,
    val comparisonCategoryName: String,
    val comparisonCount: Int,
    val message: String,
)
