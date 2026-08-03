package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.wardrobe.app.core.database.entity.SyncChangeLogEntity

@Dao
interface SyncChangeLogDao {
    /** Every outbox row past [cursor], oldest first — the exact batch to
     * send in one sync session. */
    @Query("SELECT * FROM sync_change_log WHERE id > :cursor ORDER BY id ASC")
    suspend fun changesSince(cursor: Long): List<SyncChangeLogEntity>

    @Query("SELECT COUNT(*) FROM sync_change_log WHERE id > :cursor")
    suspend fun countSince(cursor: Long): Int

    @Query("SELECT MAX(id) FROM sync_change_log")
    suspend fun latestId(): Long?

    /** Collapses the outbox to one row per (table, syncId) — an item edited
     * ten times before ever syncing only needs to be sent once. Safe to run
     * periodically (e.g. after a successful sync) since the cursor-based
     * read above only cares about `id` ordering, not row count. */
    @Query(
        """
        DELETE FROM sync_change_log
        WHERE id NOT IN (
            SELECT MAX(id) FROM sync_change_log GROUP BY tableName, syncId
        )
        """,
    )
    suspend fun compact()
}
