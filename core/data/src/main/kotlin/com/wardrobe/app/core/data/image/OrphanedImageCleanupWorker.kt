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
import com.wardrobe.app.core.database.dao.AiResultCacheDao
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.image.cleanup.OrphanedImageDetector
import com.wardrobe.app.core.image.storage.ImageFileStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Periodic, `NetworkType.NOT_REQUIRED` (Phase 1 Section 17's stated design).
 * Three independent sweeps — see phase-5b-image-pipeline.md's "Storage
 * cleanup" section: true orphans (a file with no `image_metadata` row, only
 * possible after an interrupted write), stale staging directories (an
 * abandoned capture the user never committed or discarded), stale Try-On
 * comparison-preview scratch files, and stale `ai_result_cache` rows/files
 * (RC1 hardening — three real, previously-unswept growth issues found
 * during RC1's security/resource audit: `VirtualTryOnRenderRepositoryImpl`
 * writes one `tryon_preview_*.webp` per render into `cacheDir`;
 * `AiResultCacheDao.deleteByCacheKey` existed but nothing in production ever
 * called it, so the Gateway's multi-stage cache table — and its matching
 * `AiResultImageCacheStore` files — grew without bound for the lifetime of
 * the install).
 */
@HiltWorker
class OrphanedImageCleanupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val imageMetadataDao: ImageMetadataDao,
        private val fileStore: ImageFileStore,
        private val aiResultCacheDao: AiResultCacheDao,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val referenced = imageMetadataDao.getAllFilePaths().toSet()
                val orphanCutoff = Instant.now().minus(MIN_ORPHAN_AGE_MINUTES, ChronoUnit.MINUTES)
                OrphanedImageDetector
                    .findOrphans(referenced, fileStore.allImageFilesOnDisk(), orphanCutoff)
                    .forEach { it.delete() }

                val stagingCutoff = Instant.now().minus(STALE_STAGING_AGE_HOURS, ChronoUnit.HOURS)
                OrphanedImageDetector
                    .findStaleStagingDirs(fileStore.allStagingDirectories(), stagingCutoff)
                    .forEach { it.deleteRecursively() }

                val cacheCutoff = Instant.now().minus(STALE_AI_CACHE_AGE_DAYS, ChronoUnit.DAYS)
                applicationContext.cacheDir
                    .listFiles(::isTryOnPreviewFile)
                    .orEmpty()
                    .filter { Instant.ofEpochMilli(it.lastModified()).isBefore(cacheCutoff) }
                    .forEach { it.delete() }
                File(applicationContext.cacheDir, AI_RESULT_CACHE_SUBDIRECTORY)
                    .listFiles()
                    .orEmpty()
                    .filter { Instant.ofEpochMilli(it.lastModified()).isBefore(cacheCutoff) }
                    .forEach { it.delete() }
                aiResultCacheDao.deleteOlderThan(cacheCutoff.toEpochMilli())

                Result.success()
            }

        companion object {
            /** RC2 hardening (see [OrphanedImageDetector.findOrphans]'s KDoc):
             * `ImageRepositoryImpl.commitStagedImage` moves a garment's files
             * into place, then inserts its `image_metadata` rows — a genuine,
             * if millisecond-scale, window where a just-committed file has no
             * row yet. This margin is generous relative to that real window
             * (which never involves anything slower than local file/Room IO)
             * while still reclaiming a true orphan well within the same day. */
            private const val MIN_ORPHAN_AGE_MINUTES = 60L
            private const val STALE_STAGING_AGE_HOURS = 24L

            /** Long enough that the provenance/"regenerate with the improved
             * prompt" utility ADR-012 §4/§6 designed this table for isn't lost
             * within days of normal use; short enough to bound growth for an
             * install with no other retention policy at all (see this file's
             * own class KDoc for what was found ungoverned before this fix). */
            private const val STALE_AI_CACHE_AGE_DAYS = 30L
            private const val UNIQUE_WORK_NAME = "orphaned_image_cleanup"

            /** Matches [com.wardrobe.app.core.data.repository.VirtualTryOnRenderRepositoryImpl]'s
             * own file-naming prefix exactly — kept here rather than a shared
             * constant since that class has no other reason to depend on this
             * one, and the two are already coupled by convention (same file
             * name shape), not by a shared symbol. */
            private const val TRYON_PREVIEW_PREFIX = "tryon_preview_"

            /** Matches `core:ai`'s `AiResultImageCacheStore`'s own private
             * subdirectory name exactly, for the same by-convention reason as
             * [TRYON_PREVIEW_PREFIX] — `core:data` has no other reason to
             * depend on that class. */
            private const val AI_RESULT_CACHE_SUBDIRECTORY = "ai_result_cache"

            private fun isTryOnPreviewFile(file: File): Boolean = file.name.startsWith(TRYON_PREVIEW_PREFIX)

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
