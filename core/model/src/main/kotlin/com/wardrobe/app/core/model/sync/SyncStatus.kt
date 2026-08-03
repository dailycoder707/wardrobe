package com.wardrobe.app.core.model.sync

import java.time.Instant

/** Coarse lifecycle of the sync engine right now — drives the Wardrobe Sync
 * screen's status line and Home's subtle "Wardrobe updated" confirmation. */
enum class SyncState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    SYNCING,
    ERROR,
}

/**
 * A point-in-time snapshot of where sync stands — never a technical error
 * string surfaced directly to a user (Constitution: no technical language on
 * Home); [lastError] exists for the Developer Panel and Sync History, not the
 * main UI.
 */
data class SyncStatusSnapshot(
    val state: SyncState = SyncState.IDLE,
    val connectedDevice: PairedDevice? = null,
    val lastSyncAt: Instant? = null,
    val pendingChangeCount: Int = 0,
    val storageUsedBytes: Long = 0L,
    val lastError: String? = null,
)
