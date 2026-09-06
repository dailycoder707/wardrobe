package com.wardrobe.app.core.data.repository

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.wardrobe.app.core.data.image.ImageProcessingWorker
import com.wardrobe.app.core.data.image.KEY_CROP_BOTTOM
import com.wardrobe.app.core.data.image.KEY_CROP_LEFT
import com.wardrobe.app.core.data.image.KEY_CROP_RIGHT
import com.wardrobe.app.core.data.image.KEY_CROP_TOP
import com.wardrobe.app.core.data.image.KEY_FAILURE_REASON
import com.wardrobe.app.core.data.image.KEY_HAS_CROP
import com.wardrobe.app.core.data.image.KEY_SOURCE_PATH
import com.wardrobe.app.core.data.image.KEY_STAGE
import com.wardrobe.app.core.data.image.KEY_STAGING_ID
import com.wardrobe.app.core.data.image.StagedImageStore
import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import com.wardrobe.app.core.domain.repository.ImageProcessingProgress
import com.wardrobe.app.core.domain.repository.ImageRepository
import com.wardrobe.app.core.image.pipeline.GarmentImagePipeline
import com.wardrobe.app.core.image.pipeline.retryEnhancement
import com.wardrobe.app.core.image.pipeline.retryExtraction
import com.wardrobe.app.core.image.pipeline.retryMetadata
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImageMetadata
import com.wardrobe.app.core.model.garment.ImageRetryStage
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Enqueues [ImageProcessingWorker] and translates its [WorkInfo] stream into
 * [ImageProcessingProgress] — the same shape `BackupRepositoryImpl` already
 * uses. This is the only place a [com.wardrobe.app.core.model.garment.StagedImage]
 * becomes an `image_metadata` row; `core:image` never writes to Room.
 */
class ImageRepositoryImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val pipeline: GarmentImagePipeline,
        private val fileStore: ImageFileStore,
        private val stagedImageStore: StagedImageStore,
        private val imageMetadataDao: ImageMetadataDao,
    ) : ImageRepository {
        override suspend fun analyzeQuality(sourceFilePath: String): QualityReport =
            withContext(Dispatchers.IO) {
                pipeline.quickQualityCheck(File(sourceFilePath))
            }

        override fun stageImage(
            sourceFilePath: String,
            stagingId: String,
            cropRect: NormalizedRect?,
        ): Flow<ImageProcessingProgress> {
            val inputData =
                Data
                    .Builder()
                    .putString(KEY_SOURCE_PATH, sourceFilePath)
                    .putString(KEY_STAGING_ID, stagingId)
                    .putBoolean(KEY_HAS_CROP, cropRect != null)
                    .apply {
                        if (cropRect != null) {
                            putFloat(KEY_CROP_LEFT, cropRect.left)
                            putFloat(KEY_CROP_TOP, cropRect.top)
                            putFloat(KEY_CROP_RIGHT, cropRect.right)
                            putFloat(KEY_CROP_BOTTOM, cropRect.bottom)
                        }
                    }.build()
            val request =
                OneTimeWorkRequestBuilder<ImageProcessingWorker>()
                    .addTag(ImageProcessingWorker::class.java.simpleName)
                    .setInputData(inputData)
                    .build()
            workManager.enqueueUniqueWork(stagingId, ExistingWorkPolicy.KEEP, request)
            return workManager
                .getWorkInfoByIdFlow(request.id)
                .map { info ->
                    info?.toProcessingProgress(stagingId)
                        ?: ImageProcessingProgress.InProgress(ProcessingStage.VALIDATING)
                }
        }

        override suspend fun commitStagedImage(
            stagingId: String,
            garmentId: GarmentId,
        ): List<ImageMetadata> {
            val staged = stagedImageStore.remove(stagingId) ?: return emptyList()
            return withContext(Dispatchers.IO) {
                val movedFiles = fileStore.moveStagingToGarment(stagingId, garmentId.value)
                val now = System.currentTimeMillis()
                val entities =
                    staged.variants.map { variant ->
                        val movedFile = movedFiles[variant.type] ?: File(variant.filePath)
                        ImageMetadataEntity(
                            garmentId = garmentId.value,
                            type = variant.type,
                            filePath = movedFile.path,
                            width = variant.width,
                            height = variant.height,
                            fileSizeBytes = variant.fileSizeBytes,
                            format = variant.format,
                            checksum = variant.checksum,
                            createdAt = now,
                            updatedAt = now,
                            syncId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                        )
                    }
                val ids = imageMetadataDao.insertAll(entities)
                entities.zip(ids) { entity, id -> entity.copy(id = id).toDomain() }
            }
        }

        override suspend fun discardStagedImage(stagingId: String) {
            workManager.cancelUniqueWork(stagingId)
            stagedImageStore.remove(stagingId)
            withContext(Dispatchers.IO) { fileStore.deleteStagingDirectory(stagingId) }
        }

        override suspend fun peekStagedImage(stagingId: String): StagedImage? = stagedImageStore.peek(stagingId)

        override suspend fun findGarmentIdForChecksum(checksum: String): GarmentId? =
            withContext(Dispatchers.IO) {
                imageMetadataDao.getByChecksum(checksum)?.let { GarmentId(it.garmentId) }
            }

        override fun observeImages(garmentId: GarmentId): Flow<List<ImageMetadata>> =
            imageMetadataDao.observeForGarment(garmentId.value).map { rows -> rows.map { it.toDomain() } }

        override suspend fun deleteImagesForGarment(garmentId: GarmentId) {
            withContext(Dispatchers.IO) { fileStore.deleteGarmentDirectory(garmentId.value) }
        }

        override suspend fun retryStage(
            stagingId: String,
            stage: ImageRetryStage,
        ): StagedImage {
            val previous = checkNotNull(stagedImageStore.peek(stagingId)) { "No staged image to retry for $stagingId" }
            val updated =
                withContext(Dispatchers.IO) {
                    when (stage) {
                        ImageRetryStage.EXTRACTION -> pipeline.retryExtraction(previous)
                        ImageRetryStage.ENHANCEMENT -> pipeline.retryEnhancement(previous)
                        ImageRetryStage.METADATA -> pipeline.retryMetadata(previous)
                    }
                }
            stagedImageStore.put(updated)
            return updated
        }

        private fun WorkInfo.toProcessingProgress(stagingId: String): ImageProcessingProgress =
            when (state) {
                WorkInfo.State.SUCCEEDED -> {
                    stagedImageStore.peek(stagingId)?.let { ImageProcessingProgress.Completed(it) }
                        ?: ImageProcessingProgress.Failed("Processed image result was unavailable.")
                }

                WorkInfo.State.FAILED -> {
                    ImageProcessingProgress.Failed(
                        outputData.getString(KEY_FAILURE_REASON) ?: "Image processing failed.",
                    )
                }

                WorkInfo.State.CANCELLED -> {
                    ImageProcessingProgress.Failed("Image processing was cancelled.")
                }

                else -> {
                    val stageOrdinal = progress.getInt(KEY_STAGE, ProcessingStage.VALIDATING.ordinal)
                    ImageProcessingProgress.InProgress(ProcessingStage.entries[stageOrdinal])
                }
            }
    }
