package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wardrobe.app.core.database.entity.AiCallLogEntity
import com.wardrobe.app.core.model.ai.AiCapability
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCallLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AiCallLogEntity): Long

    @Query("SELECT * FROM ai_call_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AiCallLogEntity>>

    @Query("SELECT * FROM ai_call_log WHERE capability = :capability ORDER BY timestamp DESC")
    fun observeForCapability(capability: AiCapability): Flow<List<AiCallLogEntity>>
}
