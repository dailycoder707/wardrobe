package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The sync engine's public surface (Phase 8). Everything about *how* a sync
 * session actually talks to a peer (discovery, transport, encryption,
 * conflict detection, per-table apply logic) lives in `core:sync`/
 * `core:data`; this interface is deliberately narrow so `feature:settings`
 * never needs to know any of that. See `phase-8-multi-device-sync.md`.
 */
interface SyncRepository {
    fun observeStatus(): Flow<SyncStatusSnapshot>

    /** Runs one full sync session against the currently paired device, if
     * any. Safe to call with no peer paired or no network reachable — both
     * are ordinary, silent no-ops (Constitution: sync must never be a
     * dependency the rest of the app waits on). */
    suspend fun syncNow()

    fun observeUnresolvedConflicts(): Flow<List<SyncConflict>>

    suspend fun resolveConflict(
        conflictId: Long,
        resolution: ConflictResolution,
    )

    fun observeHistory(limit: Int = DEFAULT_HISTORY_LIMIT): Flow<List<SyncHistoryEntry>>

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 20
    }
}
