package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY name")
    fun observeAll(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getById(id: Long): MaterialEntity?

    /** Phase 8 sync apply path only — no UI currently edits a material in place. */
    @Update
    suspend fun update(entity: MaterialEntity)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM materials WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): MaterialEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MaterialEntity): Long

    /** Fresh-install seeding (`WardrobeDatabase.SeedCallback`) — see that
     * class's KDoc for why an unseeded reference table would permanently
     * block AI-suggested-material auto-fill (nothing exists yet to match
     * a suggestion's name against). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MaterialEntity>)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: Long)
}
