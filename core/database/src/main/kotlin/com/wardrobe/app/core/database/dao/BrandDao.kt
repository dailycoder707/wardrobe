package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.BrandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY name")
    fun observeAll(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getById(id: Long): BrandEntity?

    /** Phase 8 sync apply path only — no UI currently edits a brand in place. */
    @Update
    suspend fun update(entity: BrandEntity)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM brands WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): BrandEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BrandEntity): Long

    @Query("DELETE FROM brands WHERE id = :id")
    suspend fun deleteById(id: Long)
}
