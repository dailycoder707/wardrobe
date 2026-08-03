package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.PackingListItemEntity
import com.wardrobe.app.core.database.entity.TripActivityEntity
import com.wardrobe.app.core.database.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TripEntity): Long

    @Update
    suspend fun update(entity: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM trips WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): TripEntity?

    @Query("SELECT * FROM trip_activities WHERE syncId = :syncId")
    suspend fun getActivityBySyncId(syncId: String): TripActivityEntity?

    @Query("SELECT * FROM packing_list_items WHERE syncId = :syncId")
    suspend fun getPackingListItemBySyncId(syncId: String): PackingListItemEntity?

    @Query("SELECT * FROM trip_activities WHERE tripId = :tripId")
    suspend fun getActivities(tripId: Long): List<TripActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<TripActivityEntity>)

    @Query("DELETE FROM trip_activities WHERE tripId = :tripId")
    suspend fun clearActivities(tripId: Long)

    @Query("SELECT * FROM packing_list_items WHERE tripId = :tripId")
    fun observePackingList(tripId: Long): Flow<List<PackingListItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackingListItems(items: List<PackingListItemEntity>)

    @Update
    suspend fun updatePackingListItem(item: PackingListItemEntity)

    /** A targeted update for the one field `TripRepository.setPacked` needs to
     * change — avoids a read-modify-write round trip through `updatePackingListItem`
     * (Phase 5a) when the caller only has an item id, not the full entity. */
    @Query("UPDATE packing_list_items SET isPacked = :isPacked WHERE id = :id")
    suspend fun setPackedState(
        id: Long,
        isPacked: Boolean,
    )

    @Query("DELETE FROM packing_list_items WHERE id = :id")
    suspend fun deletePackingListItem(id: Long)

    /** Backs `TripRepository.savePackingList`'s "replace wholesale" contract (Phase
     * 5a) — added alongside that implementation since generating a new list always
     * means starting clean, not merging with whatever the previous plan/dates left
     * behind. */
    @Query("DELETE FROM packing_list_items WHERE tripId = :tripId")
    suspend fun clearPackingList(tripId: Long)

    /**
     * Phase 8 sync — single-row insert/update, distinct from
     * [insertActivities]/[insertPackingListItems]'s "replace the whole set"
     * contract: an incoming remote change touches exactly one activity or
     * packing item, never the whole list. Deliberately `@Insert(ABORT)` +
     * `@Update`, never `onConflict = REPLACE` against an existing id — Room's
     * REPLACE is a delete-then-insert under the hood, which would fire this
     * row's own DELETE trigger and write a spurious tombstone into
     * `sync_change_log` for a row that's still very much alive.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActivity(activity: TripActivityEntity): Long

    @Update
    suspend fun updateActivity(activity: TripActivityEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPackingListItem(item: PackingListItemEntity): Long

    @Query("DELETE FROM trip_activities WHERE id = :id")
    suspend fun deleteActivityById(id: Long)
}
