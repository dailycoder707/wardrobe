package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.BodyMeasurementsEntity
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.BodyReferencePhotoEntity
import kotlinx.coroutines.flow.Flow

/** Covers the `body_profiles` aggregate root plus its two child tables
 * (`body_reference_photos`, `body_measurements`) — the same "one DAO per
 * aggregate, including its owned child rows" shape `TripDao` already
 * established for trips/activities/packing items. */
@Dao
interface BodyProfileDao {
    @Query("SELECT * FROM body_profiles LIMIT 1")
    fun observeProfile(): Flow<BodyProfileEntity?>

    @Query("SELECT * FROM body_profiles LIMIT 1")
    suspend fun getProfile(): BodyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(entity: BodyProfileEntity): Long

    @Update
    suspend fun updateProfile(entity: BodyProfileEntity)

    @Query("DELETE FROM body_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM body_profiles WHERE syncId = :syncId")
    suspend fun getProfileBySyncId(syncId: String): BodyProfileEntity?

    @Query("SELECT * FROM body_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): BodyProfileEntity?

    @Query("SELECT * FROM body_reference_photos WHERE bodyProfileId = :bodyProfileId")
    fun observePhotos(bodyProfileId: Long): Flow<List<BodyReferencePhotoEntity>>

    @Query("SELECT * FROM body_reference_photos WHERE bodyProfileId = :bodyProfileId")
    suspend fun getPhotos(bodyProfileId: Long): List<BodyReferencePhotoEntity>

    @Query("SELECT * FROM body_reference_photos WHERE bodyProfileId = :bodyProfileId AND pose = :pose")
    suspend fun getPhoto(
        bodyProfileId: Long,
        pose: String,
    ): BodyReferencePhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(entity: BodyReferencePhotoEntity): Long

    /** Phase 8-style image-transfer dedup, extended to body photos — see
     * `runImageTransferPhase`'s Phase 10 update. */
    @Query("SELECT checksum FROM body_reference_photos WHERE checksum IS NOT NULL")
    suspend fun getAllPhotoChecksums(): List<String>

    @Query("SELECT * FROM body_reference_photos WHERE checksum = :checksum LIMIT 1")
    suspend fun getPhotoByChecksum(checksum: String): BodyReferencePhotoEntity?

    @Query("SELECT * FROM body_measurements WHERE bodyProfileId = :bodyProfileId")
    fun observeMeasurements(bodyProfileId: Long): Flow<BodyMeasurementsEntity?>

    @Query("SELECT * FROM body_measurements WHERE bodyProfileId = :bodyProfileId")
    suspend fun getMeasurements(bodyProfileId: Long): BodyMeasurementsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurements(entity: BodyMeasurementsEntity): Long
}
