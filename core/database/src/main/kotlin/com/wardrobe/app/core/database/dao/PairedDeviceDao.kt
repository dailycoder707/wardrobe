package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.PairedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PairedDeviceEntity)

    @Query("SELECT * FROM paired_device")
    fun observeAll(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_device WHERE deviceId = :deviceId")
    suspend fun getById(deviceId: String): PairedDeviceEntity?

    @Query("DELETE FROM paired_device WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query(
        "UPDATE paired_device SET lastSyncAt = :syncedAt, lastSyncedChangeLogId = :cursor " +
            "WHERE deviceId = :deviceId",
    )
    suspend fun updateSyncCursor(
        deviceId: String,
        syncedAt: Long,
        cursor: Long,
    )
}
