package com.wardrobe.app.core.data.repository

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wardrobe.app.core.data.backup.BackupExportWorker
import com.wardrobe.app.core.data.backup.BackupRestoreWorker
import com.wardrobe.app.core.data.backup.KEY_DESTINATION_URI
import com.wardrobe.app.core.data.backup.KEY_FAILURE_REASON
import com.wardrobe.app.core.data.backup.KEY_PROGRESS_FRACTION
import com.wardrobe.app.core.data.backup.KEY_RESULT_FILE_PATH
import com.wardrobe.app.core.data.backup.KEY_SOURCE_URI
import com.wardrobe.app.core.domain.repository.BackupProgress
import com.wardrobe.app.core.domain.repository.BackupRepository
import com.wardrobe.app.core.domain.repository.RestoreProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Enqueues [BackupExportWorker]/[BackupRestoreWorker] and translates their
 * [WorkInfo] stream into the domain's [BackupProgress]/[RestoreProgress] — the
 * actual file logic lives in `core:data/backup`, not here (see
 * phase-5a-data-layer.md). Using `WorkManager`, not a bare coroutine launched on
 * some scope, is what gives large-file backup/restore a chance of surviving the app
 * being backgrounded mid-operation.
 */
class BackupRepositoryImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : BackupRepository {
        override fun exportBackup(destinationUri: String): Flow<BackupProgress> {
            val request =
                OneTimeWorkRequestBuilder<BackupExportWorker>()
                    .addTag(BackupExportWorker::class.java.simpleName)
                    .setInputData(Data.Builder().putString(KEY_DESTINATION_URI, destinationUri).build())
                    .build()
            workManager.enqueue(request)
            return workManager
                .getWorkInfoByIdFlow(request.id)
                .map { info -> info?.toBackupProgress() ?: BackupProgress.InProgress(0f) }
        }

        override fun restoreBackup(sourceUri: String): Flow<RestoreProgress> {
            val request =
                OneTimeWorkRequestBuilder<BackupRestoreWorker>()
                    .addTag(BackupRestoreWorker::class.java.simpleName)
                    .setInputData(Data.Builder().putString(KEY_SOURCE_URI, sourceUri).build())
                    .build()
            workManager.enqueue(request)
            return workManager
                .getWorkInfoByIdFlow(request.id)
                .map { info -> info?.toRestoreProgress() ?: RestoreProgress.InProgress(0f) }
        }

        private fun WorkInfo.toBackupProgress(): BackupProgress =
            when (state) {
                WorkInfo.State.SUCCEEDED -> {
                    BackupProgress.Complete(
                        outputData.getString(KEY_RESULT_FILE_PATH).orEmpty(),
                    )
                }

                WorkInfo.State.FAILED -> {
                    BackupProgress.Failed(
                        outputData.getString(KEY_FAILURE_REASON) ?: "Backup failed.",
                    )
                }

                WorkInfo.State.CANCELLED -> {
                    BackupProgress.Failed("Backup was cancelled.")
                }

                else -> {
                    BackupProgress.InProgress(progress.getFloat(KEY_PROGRESS_FRACTION, 0f))
                }
            }

        private fun WorkInfo.toRestoreProgress(): RestoreProgress =
            when (state) {
                WorkInfo.State.SUCCEEDED -> {
                    RestoreProgress.Complete
                }

                WorkInfo.State.FAILED -> {
                    RestoreProgress.Failed(
                        outputData.getString(KEY_FAILURE_REASON) ?: "Restore failed.",
                    )
                }

                WorkInfo.State.CANCELLED -> {
                    RestoreProgress.Failed("Restore was cancelled.")
                }

                else -> {
                    RestoreProgress.InProgress(progress.getFloat(KEY_PROGRESS_FRACTION, 0f))
                }
            }
    }
