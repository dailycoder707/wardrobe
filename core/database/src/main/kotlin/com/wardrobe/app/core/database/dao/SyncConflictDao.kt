package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wardrobe.app.core.database.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {
    @Insert
    suspend fun insert(entity: SyncConflictEntity): Long

    @Query("SELECT * FROM sync_conflict WHERE resolvedAt IS NULL ORDER BY detectedAt DESC")
    fun observeUnresolved(): Flow<List<SyncConflictEntity>>

    @Query("UPDATE sync_conflict SET resolvedAt = :resolvedAt, resolution = :resolution WHERE id = :id")
    suspend fun resolve(
        id: Long,
        resolvedAt: Long,
        resolution: String,
    )

    @Query("SELECT COUNT(*) FROM sync_conflict WHERE resolvedAt IS NOT NULL")
    suspend fun resolvedCount(): Int
}
