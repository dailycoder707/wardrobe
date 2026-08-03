package com.wardrobe.app.core.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.wardrobe.app.core.database.WardrobeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val KEY_DESTINATION_URI = "destination_uri"
const val KEY_PROGRESS_FRACTION = "progress_fraction"
const val KEY_RESULT_FILE_PATH = "result_file_path"
const val KEY_FAILURE_REASON = "failure_reason"

/**
 * Runs the real export logic (`BackupFileOperations`) as a `WorkManager` job so it
 * survives the app being backgrounded mid-export (Phase 1 Section 19/20 — large
 * file I/O). `BackupRepositoryImpl` enqueues this and maps its `WorkInfo` back into
 * `BackupProgress`; this class has no knowledge of that mapping.
 */
@HiltWorker
class BackupExportWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val database: WardrobeDatabase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val destinationUriString =
                    inputData.getString(KEY_DESTINATION_URI)
                        ?: return@withContext Result.failure(failureData("No destination was chosen."))

                try {
                    val destinationUri = android.net.Uri.parse(destinationUriString)
                    val outputStream =
                        applicationContext.contentResolver.openOutputStream(destinationUri)
                            ?: return@withContext Result.failure(
                                failureData("Couldn't open that location for writing."),
                            )

                    // A full checkpoint is required before copying the .db file — Room runs
                    // in WAL mode, so the main file alone isn't guaranteed consistent
                    // otherwise. See phase-5a-data-layer.md.
                    database.checkpoint()

                    outputStream.use { out ->
                        BackupFileOperations.export(
                            destination = out,
                            paths =
                                BackupPaths(
                                    dbFile = applicationContext.getDatabasePath(WardrobeDatabase.DATABASE_NAME),
                                    datastoreDir = File(applicationContext.filesDir, "datastore"),
                                    imagesDir = File(applicationContext.filesDir, "images"),
                                ),
                            schemaVersion = 1,
                            appVersionName = appVersionName(),
                            onProgress = { fraction -> setProgress(progressData(fraction)) },
                        )
                    }

                    Result.success(
                        Data.Builder().putString(KEY_RESULT_FILE_PATH, destinationUriString).build(),
                    )
                } catch (e: java.io.IOException) {
                    Result.failure(failureData(e.message ?: "Backup failed."))
                }
            }

        private fun appVersionName(): String =
            runCatching {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
            }.getOrNull() ?: "unknown"

        private fun progressData(fraction: Float) = Data.Builder().putFloat(KEY_PROGRESS_FRACTION, fraction).build()

        private fun failureData(reason: String) = Data.Builder().putString(KEY_FAILURE_REASON, reason).build()
    }
