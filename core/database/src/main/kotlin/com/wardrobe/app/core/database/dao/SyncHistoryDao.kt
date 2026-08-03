package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.SyncHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHistoryDao {
    @Insert
    suspend fun insert(entity: SyncHistoryEntity): Long

    @Update
    suspend fun update(entity: SyncHistoryEntity)

    @Query("SELECT * FROM sync_history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE outcome = 'SUCCESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastSuccess(): SyncHistoryEntity?

    @Query("SELECT * FROM sync_history WHERE outcome = 'FAILED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastFailure(): SyncHistoryEntity?
}
