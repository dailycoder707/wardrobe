package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.WishlistItemId
import com.wardrobe.app.core.model.wishlist.WishlistItem
import kotlinx.coroutines.flow.Flow

interface WishlistRepository {
    fun observeActive(): Flow<List<WishlistItem>>

    suspend fun addItem(item: WishlistItem): WishlistItemId

    suspend fun updateItem(item: WishlistItem)

    suspend fun deleteItem(id: WishlistItemId)
}
