package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.ImportQueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ImportQueueItemEntity>): List<Long>

    @Update
    suspend fun update(entity: ImportQueueItemEntity)

    @Query("SELECT * FROM import_queue_items ORDER BY createdAt")
    fun observeAll(): Flow<List<ImportQueueItemEntity>>

    @Query("SELECT COUNT(*) FROM import_queue_items WHERE status != 'COMPLETED'")
    fun observeIncompleteCount(): Flow<Int>

    @Query("SELECT * FROM import_queue_items WHERE id = :id")
    suspend fun getById(id: Long): ImportQueueItemEntity?

    @Query("DELETE FROM import_queue_items WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()
}
