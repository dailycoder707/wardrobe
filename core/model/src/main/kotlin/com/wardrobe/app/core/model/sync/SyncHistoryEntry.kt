package com.wardrobe.app.core.model.sync

import java.time.Instant

enum class SyncOutcome {
    SUCCESS,
    FAILED,
    PARTIAL,
}

/** One completed (or abandoned) sync session — backs the Wardrobe Sync
 * screen's "Sync History" list and the Developer Panel's last-success/
 * last-failure rows. */
data class SyncHistoryEntry(
    val id: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val outcome: SyncOutcome,
    val changesSent: Int,
    val changesReceived: Int,
    val bytesSent: Long,
    val bytesReceived: Long,
    val conflictsDetected: Int,
    val errorMessage: String? = null,
)
