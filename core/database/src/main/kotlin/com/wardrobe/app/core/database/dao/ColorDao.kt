package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.ColorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorDao {
    @Query("SELECT * FROM colors ORDER BY name")
    fun observeAll(): Flow<List<ColorEntity>>

    @Query("SELECT * FROM colors WHERE id = :id")
    suspend fun getById(id: Long): ColorEntity?

    /** Phase 8 sync apply path only — no UI currently edits a color in place. */
    @Update
    suspend fun update(entity: ColorEntity)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM colors WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): ColorEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ColorEntity): Long

    @Query("DELETE FROM colors WHERE id = :id")
    suspend fun deleteById(id: Long)
}
