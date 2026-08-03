package com.wardrobe.app.core.data.image

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.wardrobe.app.core.image.pipeline.GarmentImagePipeline
import com.wardrobe.app.core.image.pipeline.ImageProcessingException
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.ProcessingStage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

const val KEY_SOURCE_PATH = "source_path"
const val KEY_STAGING_ID = "staging_id"
const val KEY_HAS_CROP = "has_crop"
const val KEY_CROP_LEFT = "crop_left"
const val KEY_CROP_TOP = "crop_top"
const val KEY_CROP_RIGHT = "crop_right"
const val KEY_CROP_BOTTOM = "crop_bottom"
const val KEY_STAGE = "stage"
const val KEY_FAILURE_REASON = "failure_reason"

/**
 * Runs `GarmentImagePipeline.process` as a `WorkManager` job so it survives
 * the app being backgrounded mid-import — the same reason
 * `BackupExportWorker`/`BackupRestoreWorker` (Phase 5a) are workers rather
 * than a bare suspend call. `ImageRepositoryImpl` enqueues this (unique work
 * keyed by staging id) and maps its `WorkInfo` into `ImageProcessingProgress`;
 * this class doesn't know about that mapping.
 */
@HiltWorker
class ImageProcessingWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val pipeline: GarmentImagePipeline,
        private val stagedImageStore: StagedImageStore,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val sourcePath =
                    inputData.getString(KEY_SOURCE_PATH)
                        ?: return@withContext Result.failure(failureData("No source file was provided."))
                val stagingId =
                    inputData.getString(KEY_STAGING_ID)
                        ?: return@withContext Result.failure(failureData("No staging id was provided."))

                try {
                    val result =
                        pipeline.process(
                            sourceFile = File(sourcePath),
                            stagingId = stagingId,
                            cropRect = readCropRect(),
                            onProgress = { stage -> setProgress(progressData(stage)) },
                        )
                    stagedImageStore.put(result)
                    Result.success()
                } catch (e: ImageProcessingException) {
                    Result.failure(failureData(e.message ?: "Image processing failed."))
                } catch (e: IOException) {
                    Result.failure(failureData(e.message ?: "Image processing failed."))
                }
            }

        private fun readCropRect(): NormalizedRect? {
            if (!inputData.getBoolean(KEY_HAS_CROP, false)) return null
            return NormalizedRect(
                left = inputData.getFloat(KEY_CROP_LEFT, 0f),
                top = inputData.getFloat(KEY_CROP_TOP, 0f),
                right = inputData.getFloat(KEY_CROP_RIGHT, 1f),
                bottom = inputData.getFloat(KEY_CROP_BOTTOM, 1f),
            )
        }

        private fun progressData(stage: ProcessingStage) = Data.Builder().putInt(KEY_STAGE, stage.ordinal).build()

        private fun failureData(reason: String) = Data.Builder().putString(KEY_FAILURE_REASON, reason).build()
    }
