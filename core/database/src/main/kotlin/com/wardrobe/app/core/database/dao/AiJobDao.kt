package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.AiJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AiJobEntity): Long

    @Update
    suspend fun update(entity: AiJobEntity)

    @Query("SELECT * FROM ai_jobs WHERE cacheKey = :cacheKey ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): AiJobEntity?

    @Query("SELECT * FROM ai_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiJobEntity>>

    @Query("DELETE FROM ai_jobs WHERE status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished()
}
