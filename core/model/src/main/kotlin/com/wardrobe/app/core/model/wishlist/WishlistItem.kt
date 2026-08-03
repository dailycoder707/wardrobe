package com.wardrobe.app.core.model.wishlist

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.common.WishlistItemId
import java.time.Instant

/**
 * Manually-entered — no live pricing, no shopping catalogue (Section 0 tier table,
 * `alta-class-closet-app-master-prompt.md`: Shopping feed is CUT).
 */
data class WishlistItem(
    val id: WishlistItemId,
    val name: String,
    val photoUri: String?,
    val notes: String?,
    val estimatedPrice: Money?,
    val categoryId: CategoryId?,
    val brandId: BrandId?,
    val priority: Int?,
    val isPurchased: Boolean,
    val createdAt: Instant,
)
