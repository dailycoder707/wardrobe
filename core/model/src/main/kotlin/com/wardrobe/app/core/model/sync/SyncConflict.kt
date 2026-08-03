package com.wardrobe.app.core.model.sync

import java.time.Instant

/**
 * Why a change couldn't be resolved automatically — the only two cases this
 * app's deterministic rules (newest-wins, merge collections, newest-photo-
 * wins) cannot settle on their own. See `phase-8-multi-device-sync.md`'s
 * "Conflict resolution" section for the exact decision table.
 */
enum class ConflictReason {
    /** The same row was deleted on one device and edited on the other since
     * they last synced — deleting could discard a real edit, so this is
     * surfaced instead of guessed at. */
    EDIT_DELETE_CONFLICT,
}

/** The user's choice for a surfaced [SyncConflict] — there is no third
 * "merge" option for this kind of conflict; a deleted-vs-edited row is
 * binary by nature. */
enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE,
}

/**
 * One user-visible conflict card (Constitution: "never silently lose data").
 * [localSummary]/[remoteSummary] are short, human-readable descriptions
 * (e.g. "Edited on this device: renamed to 'Navy Blazer'" / "Deleted on
 * Tablet") — never raw JSON or entity dumps.
 */
data class SyncConflict(
    val id: Long,
    val entityType: SyncEntityType,
    val entitySyncId: String,
    val reason: ConflictReason,
    val localSummary: String,
    val remoteSummary: String,
    val detectedAt: Instant,
    val resolvedAt: Instant? = null,
    val resolution: ConflictResolution? = null,
)
