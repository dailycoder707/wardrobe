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

const val KEY_SOURCE_URI = "source_uri"

/**
 * Restores from a `.wardrobebackup` file, replacing the live database, DataStore
 * file, and images directory wholesale. **Requires an app restart afterward** — see
 * phase-5a-data-layer.md's "restart decision": there is no supported way to reopen a
 * new Room instance into this process's existing Hilt singleton component once the
 * underlying file has been swapped out from under it. This worker only closes the
 * database and replaces files; telling the user to reopen the app is the eventual
 * Settings UI's job (Phase 5f), not this class's.
 */
@HiltWorker
class BackupRestoreWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val database: WardrobeDatabase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val sourceUriString =
                    inputData.getString(KEY_SOURCE_URI)
                        ?: return@withContext Result.failure(failureData("No backup file was chosen."))

                try {
                    val sourceUri = android.net.Uri.parse(sourceUriString)
                    val inputStream =
                        applicationContext.contentResolver.openInputStream(sourceUri)
                            ?: return@withContext Result.failure(failureData("Couldn't open that file."))

                    val targetPaths =
                        BackupPaths(
                            dbFile = applicationContext.getDatabasePath(WardrobeDatabase.DATABASE_NAME),
                            datastoreDir = File(applicationContext.filesDir, "datastore"),
                            imagesDir = File(applicationContext.filesDir, "images"),
                        )

                    // Close the live database before overwriting its file — Room holds an
                    // open file handle otherwise, and the eventual UI is responsible for
                    // telling the user to reopen the app once this completes (see class KDoc).
                    database.close()

                    val manifest =
                        inputStream.use { input ->
                            BackupFileOperations.import(
                                source = input,
                                targetPaths = targetPaths,
                                onProgress = { fraction -> setProgress(progressData(fraction)) },
                            )
                        }

                    val schemaVersion = manifest.getProperty(BackupFileOperations.SCHEMA_VERSION_KEY)?.toIntOrNull()
                    if (schemaVersion == null || schemaVersion > 1) {
                        return@withContext Result.failure(
                            failureData("This backup may be from a newer version of the app."),
                        )
                    }

                    Result.success()
                } catch (e: java.io.IOException) {
                    Result.failure(failureData(e.message ?: "Restore failed."))
                }
            }

        private fun progressData(fraction: Float) = Data.Builder().putFloat(KEY_PROGRESS_FRACTION, fraction).build()

        private fun failureData(reason: String) = Data.Builder().putString(KEY_FAILURE_REASON, reason).build()
    }
