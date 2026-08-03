package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GarmentPlacementTemplateDao {
    @Query("SELECT * FROM garment_placement_templates WHERE bodyProfileId = :bodyProfileId AND garmentId = :garmentId")
    fun observeForGarment(
        bodyProfileId: Long,
        garmentId: Long,
    ): Flow<List<GarmentPlacementTemplateEntity>>

    @Query("SELECT * FROM garment_placement_templates WHERE id = :id")
    suspend fun getById(id: Long): GarmentPlacementTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: GarmentPlacementTemplateEntity): Long

    @Update
    suspend fun update(entity: GarmentPlacementTemplateEntity)

    @Query("DELETE FROM garment_placement_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE garment_placement_templates SET lastUsedAt = :usedAt WHERE id = :id")
    suspend fun markUsed(
        id: Long,
        usedAt: Long,
    )

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM garment_placement_templates WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): GarmentPlacementTemplateEntity?
}
