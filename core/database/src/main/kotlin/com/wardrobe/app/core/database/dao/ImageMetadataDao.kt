package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMetadataDao {
    @Query("SELECT * FROM image_metadata WHERE garmentId = :garmentId")
    suspend fun getForGarment(garmentId: Long): List<ImageMetadataEntity>

    /** Added Phase 5b for `ImageRepository.observeImages` — the suspend
     * [getForGarment] above is a one-shot read, used where a Flow isn't needed. */
    @Query("SELECT * FROM image_metadata WHERE garmentId = :garmentId")
    fun observeForGarment(garmentId: Long): Flow<List<ImageMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ImageMetadataEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ImageMetadataEntity): Long

    @Query("DELETE FROM image_metadata WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM image_metadata WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): ImageMetadataEntity?

    /** Every checksum this device already has a file for — the manifest
     * side of "never resend an identical image." */
    @Query("SELECT DISTINCT checksum FROM image_metadata WHERE checksum IS NOT NULL")
    suspend fun getAllChecksums(): List<String>

    @Query("SELECT * FROM image_metadata WHERE checksum = :checksum LIMIT 1")
    suspend fun getByChecksum(checksum: String): ImageMetadataEntity?

    /** Every stored file path currently referenced — the `OrphanedImageCleanupWorker`
     * (Phase 1 Section 17, `core:image`) diffs this against the files on disk. */
    @Query("SELECT filePath FROM image_metadata")
    suspend fun getAllFilePaths(): List<String>
}
