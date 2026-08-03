package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.garment.ImportQueueItem
import kotlinx.coroutines.flow.Flow

/**
 * The Add-to-Wardrobe import queue's single source of truth (Room-backed,
 * device-local only — see `ImportQueueItemEntity`'s KDoc). The queue screen
 * always observes [observeQueue] rather than holding its own in-memory list,
 * so "start a new import" ([enqueue]) and "resume one interrupted by an app
 * restart or crash" are the same code path, not two.
 */
interface ImportQueueRepository {
    /** Inserts one `PENDING` row per file and returns them (with assigned
     * ids) so the caller can start processing immediately without waiting
     * for [observeQueue] to re-emit. */
    suspend fun enqueue(filePaths: List<String>): List<ImportQueueItem>

    fun observeQueue(): Flow<List<ImportQueueItem>>

    /** Drives `HomeScreen`'s "Resume Import" banner — a nonzero count means
     * an import didn't reach [com.wardrobe.app.core.model.garment.ImportQueueItemStatus.COMPLETED]. */
    fun observeIncompleteCount(): Flow<Int>

    /** Persists this item's current state — the queue ViewModel's only write
     * path as it drives an item through staging/review/save. */
    suspend fun updateItem(item: ImportQueueItem)

    suspend fun deleteCompleted()
}
