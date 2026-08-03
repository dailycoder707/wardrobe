package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.GarmentMaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GarmentMaskDao {
    @Query("SELECT * FROM garment_masks WHERE bodyProfileId = :bodyProfileId AND garmentId = :garmentId")
    fun observe(
        bodyProfileId: Long,
        garmentId: Long,
    ): Flow<GarmentMaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GarmentMaskEntity): Long

    @Query("DELETE FROM garment_masks WHERE bodyProfileId = :bodyProfileId AND garmentId = :garmentId")
    suspend fun clear(
        bodyProfileId: Long,
        garmentId: Long,
    )

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM garment_masks WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): GarmentMaskEntity?

    /** Phase 8-style image-transfer dedup, extended to mask images — see
     * `runImageTransferPhase`'s Phase 10 update. */
    @Query("SELECT checksum FROM garment_masks WHERE checksum IS NOT NULL")
    suspend fun getAllChecksums(): List<String>

    @Query("SELECT * FROM garment_masks WHERE checksum = :checksum LIMIT 1")
    suspend fun getByChecksum(checksum: String): GarmentMaskEntity?
}
