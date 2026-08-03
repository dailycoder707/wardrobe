package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.StyleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StyleRuleDao {
    @Query("SELECT * FROM style_rules WHERE isActive = 1 ORDER BY createdAt")
    fun observeActive(): Flow<List<StyleRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: StyleRuleEntity): Long

    @Update
    suspend fun update(entity: StyleRuleEntity)

    @Query("DELETE FROM style_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM style_rules WHERE id = :id")
    suspend fun getById(id: Long): StyleRuleEntity?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM style_rules WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): StyleRuleEntity?
}
