package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items WHERE isPurchased = 0 ORDER BY priority, createdAt DESC")
    fun observeActive(): Flow<List<WishlistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WishlistItemEntity): Long

    @Update
    suspend fun update(entity: WishlistItemEntity)

    @Query("DELETE FROM wishlist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    suspend fun getById(id: Long): WishlistItemEntity?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM wishlist_items WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): WishlistItemEntity?
}
