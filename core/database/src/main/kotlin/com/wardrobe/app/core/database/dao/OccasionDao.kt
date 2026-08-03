package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.OccasionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OccasionDao {
    @Query("SELECT * FROM occasions ORDER BY name")
    fun observeAll(): Flow<List<OccasionEntity>>

    @Query("SELECT * FROM occasions WHERE id = :id")
    suspend fun getById(id: Long): OccasionEntity?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM occasions WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): OccasionEntity?

    /** Phase 8 sync apply path only — no UI currently edits an occasion in place. */
    @Update
    suspend fun update(entity: OccasionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OccasionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<OccasionEntity>)

    @Query("DELETE FROM occasions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
