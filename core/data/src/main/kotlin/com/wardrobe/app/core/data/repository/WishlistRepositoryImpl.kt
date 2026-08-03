package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.WishlistDao
import com.wardrobe.app.core.domain.repository.WishlistRepository
import com.wardrobe.app.core.model.common.WishlistItemId
import com.wardrobe.app.core.model.wishlist.WishlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WishlistRepositoryImpl
    @Inject
    constructor(
        private val dao: WishlistDao,
    ) : WishlistRepository {
        override fun observeActive(): Flow<List<WishlistItem>> =
            dao.observeActive().map { rows -> rows.map { it.toDomain() } }

        override suspend fun addItem(item: WishlistItem): WishlistItemId =
            WishlistItemId(
                dao.insert(
                    item.toEntity().copy(
                        id = 0,
                        syncId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                ),
            )

        /** Phase 8: syncId must survive an edit unchanged, same reasoning as
         * `GarmentRepositoryImpl.updateGarment`. */
        override suspend fun updateItem(item: WishlistItem) {
            val existingSyncId = dao.getById(item.id.value)?.syncId.orEmpty()
            dao.update(item.toEntity().copy(syncId = existingSyncId, updatedAt = System.currentTimeMillis()))
        }

        override suspend fun deleteItem(id: WishlistItemId) = dao.deleteById(id.value)
    }
