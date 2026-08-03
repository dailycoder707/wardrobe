package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.WearEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WearEventDao {
    /** Callers must have already validated the garmentId XOR outfitId invariant
     * (core:model's `WearEvent` constructor does this) before mapping to this entity
     * — the schema itself can't enforce it, see phase-3-persistence.md. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WearEventEntity): Long

    /** Backs Calendar's "quick reschedule" and "confirm as worn" (Phase 5d) — an
     * edit in place rather than delete-then-reinsert, so the row's `id` (and
     * anything referencing it) survives the change. */
    @Update
    suspend fun update(entity: WearEventEntity)

    @Query("DELETE FROM wear_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM wear_events WHERE id = :id")
    suspend fun getById(id: Long): WearEventEntity?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM wear_events WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): WearEventEntity?

    @Query("DELETE FROM wear_events WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("SELECT * FROM wear_events WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeInRange(
        startDate: String,
        endDate: String,
    ): Flow<List<WearEventEntity>>

    @Query("SELECT * FROM wear_events WHERE date = :date ORDER BY createdAt")
    suspend fun getForDate(date: String): List<WearEventEntity>

    @Query("SELECT * FROM wear_events WHERE garmentId = :garmentId ORDER BY date DESC")
    suspend fun getForGarment(garmentId: Long): List<WearEventEntity>

    @Query("SELECT * FROM wear_events WHERE outfitId IN (:outfitIds) ORDER BY date DESC")
    suspend fun getForOutfits(outfitIds: List<Long>): List<WearEventEntity>
}
