package com.wardrobe.app.core.data.image

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.image.cleanup.OrphanedImageDetector
import com.wardrobe.app.core.image.storage.ImageFileStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Periodic, `NetworkType.NOT_REQUIRED` (Phase 1 Section 17's stated design).
 * Two independent sweeps — see phase-5b-image-pipeline.md's "Storage cleanup"
 * section: true orphans (a file with no `image_metadata` row, only possible
 * after an interrupted write) and stale staging directories (an abandoned
 * capture the user never committed or discarded).
 */
@HiltWorker
class OrphanedImageCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val imageMetadataDao: ImageMetadataDao,
        private val fileStore: ImageFileStore,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val referenced = imageMetadataDao.getAllFilePaths().toSet()
                OrphanedImageDetector
                    .findOrphans(referenced, fileStore.allImageFilesOnDisk())
                    .forEach { it.delete() }

                val cutoff = Instant.now().minus(STALE_STAGING_AGE_HOURS, ChronoUnit.HOURS)
                OrphanedImageDetector
                    .findStaleStagingDirs(fileStore.allStagingDirectories(), cutoff)
                    .forEach { it.deleteRecursively() }

                Result.success()
            }

        companion object {
            private const val STALE_STAGING_AGE_HOURS = 24L
            private const val UNIQUE_WORK_NAME = "orphaned_image_cleanup"

            fun schedule(workManager: WorkManager) {
                val request =
                    PeriodicWorkRequestBuilder<OrphanedImageCleanupWorker>(1, TimeUnit.DAYS)
                        .addTag(OrphanedImageCleanupWorker::class.java.simpleName)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build(),
                        ).build()
                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
