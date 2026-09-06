package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.FabricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FabricDao {
    @Query("SELECT * FROM fabrics ORDER BY name")
    fun observeAll(): Flow<List<FabricEntity>>

    @Query("SELECT * FROM fabrics WHERE id = :id")
    suspend fun getById(id: Long): FabricEntity?

    /** Phase 8 sync apply path only — no UI currently edits a fabric in place. */
    @Update
    suspend fun update(entity: FabricEntity)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM fabrics WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): FabricEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FabricEntity): Long

    /** Fresh-install seeding (`WardrobeDatabase.SeedCallback`). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<FabricEntity>)

    @Query("DELETE FROM fabrics WHERE id = :id")
    suspend fun deleteById(id: Long)
}
