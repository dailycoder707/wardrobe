package com.wardrobe.app.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface BackupProgress {
    data class InProgress(
        val fractionComplete: Float,
    ) : BackupProgress

    data class Complete(
        val filePath: String,
    ) : BackupProgress

    data class Failed(
        val reason: String,
    ) : BackupProgress
}

sealed interface RestoreProgress {
    data class InProgress(
        val fractionComplete: Float,
    ) : RestoreProgress

    data object Complete : RestoreProgress

    data class Failed(
        val reason: String,
    ) : RestoreProgress
}

/**
 * User-triggered only (Phase 1 Section 19/20) — no scheduled auto-backup. The
 * implementation (Phase 5a) runs as a foreground-service-backed WorkManager job; this
 * interface's `Flow` is how that job's progress reaches the Settings UI.
 */
interface BackupRepository {
    fun exportBackup(destinationUri: String): Flow<BackupProgress>

    fun restoreBackup(sourceUri: String): Flow<RestoreProgress>
}
