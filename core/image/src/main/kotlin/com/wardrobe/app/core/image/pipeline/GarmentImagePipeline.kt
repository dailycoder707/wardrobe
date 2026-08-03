package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.image.hashing.ImageHasher
import com.wardrobe.app.core.image.quality.ImageQualityAnalyzer
import com.wardrobe.app.core.image.segmentation.BackgroundRemover
import com.wardrobe.app.core.image.segmentation.CutoutResult
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.image.validation.ImageValidator
import com.wardrobe.app.core.image.validation.ValidationResult
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Orchestrates one photo through every stage in phase-5b-image-pipeline.md's
 * pipeline diagram. Returns plain data ([StagedImage]) — this class has no
 * Room dependency and never writes an `image_metadata` row; that's
 * `core:data`'s `ImageRepositoryImpl` job, same layering Phase 5a established
 * for every other repository.
 */
@Singleton
class GarmentImagePipeline
    @Inject
    constructor(
        private val fileStore: ImageFileStore,
        private val backgroundRemover: BackgroundRemover,
        private val qualityAnalyzer: ImageQualityAnalyzer,
    ) {
        /**
         * Fast path: decode capped at a small target size and run the quality
         * checks only — no file writes, no background removal. Not `suspend`:
         * it makes no suspending calls itself, so an `override suspend fun` at
         * the repository layer can call this directly without a redundant
         * modifier here (Detekt's `RedundantSuspendModifier`).
         */
        fun quickQualityCheck(sourceFile: File): QualityReport {
            validateOrThrow(sourceFile, ProcessingStage.VALIDATING)
            val orientation = ExifOrientation.readOrientation(sourceFile)
            val bitmap = ExifOrientation.correct(decodeCapped(sourceFile, QUICK_CHECK_LONG_EDGE_PX), orientation)
            return qualityAnalyzer.analyze(bitmap)
        }

        suspend fun process(
            sourceFile: File,
            stagingId: String,
            cropRect: NormalizedRect?,
            onProgress: suspend (ProcessingStage) -> Unit = {},
        ): StagedImage {
            onProgress(ProcessingStage.VALIDATING)
            validateOrThrow(sourceFile, ProcessingStage.VALIDATING)

            onProgress(ProcessingStage.CORRECTING_ORIENTATION)
            val orientation = ExifOrientation.readOrientation(sourceFile)
            var bitmap = decodeCapped(sourceFile, ORIGINAL_LONG_EDGE_PX)
            bitmap = ExifOrientation.correct(bitmap, orientation)
            bitmap = ImageResizer.resizeToLongEdge(bitmap, ORIGINAL_LONG_EDGE_PX)

            onProgress(ProcessingStage.SAVING_ORIGINAL)
            val stagingDir = fileStore.ensureExists(fileStore.stagingDir(stagingId))
            val originalFile = fileStore.fileFor(stagingDir, ImageType.ORIGINAL)
            writeOrThrow(ProcessingStage.SAVING_ORIGINAL) {
                originalFile.outputStream().use { ImageResizer.encodeJpeg(bitmap, ORIGINAL_JPEG_QUALITY, it) }
            }

            onProgress(ProcessingStage.ANALYZING_QUALITY)
            val qualityReport = qualityAnalyzer.analyze(bitmap)

            onProgress(ProcessingStage.CROPPING)
            val workingBitmap = if (cropRect != null) ImageCropper.crop(bitmap, cropRect) else bitmap

            onProgress(ProcessingStage.REMOVING_BACKGROUND)
            val outcome = removeBackground(workingBitmap, stagingDir)

            onProgress(ProcessingStage.GENERATING_THUMBNAIL)
            val thumbnailFile = writeThumbnail(outcome.thumbnailSource, stagingDir)

            onProgress(ProcessingStage.HASHING)
            val variants = buildVariants(originalFile, bitmap, outcome, thumbnailFile)

            onProgress(ProcessingStage.DONE)
            return StagedImage(stagingId, variants, qualityReport, outcome.status, outcome.confidence)
        }

        private class BackgroundRemovalOutcome(
            val cutoutFile: File?,
            val cutoutBitmap: Bitmap?,
            val status: BackgroundRemovalStatus,
            val confidence: Float?,
            val thumbnailSource: Bitmap,
        )

        private suspend fun removeBackground(
            workingBitmap: Bitmap,
            stagingDir: File,
        ): BackgroundRemovalOutcome =
            when (val cutout = backgroundRemover.removeBackground(workingBitmap)) {
                is CutoutResult.Success -> {
                    val file = fileStore.fileFor(stagingDir, ImageType.CUTOUT)
                    writeOrThrow(ProcessingStage.REMOVING_BACKGROUND) {
                        file.outputStream().use { ImageResizer.encodeWebpLossless(cutout.bitmap, it) }
                    }
                    BackgroundRemovalOutcome(
                        cutoutFile = file,
                        cutoutBitmap = cutout.bitmap,
                        status = BackgroundRemovalStatus.SUCCEEDED,
                        confidence = cutout.confidence,
                        thumbnailSource = cutout.bitmap,
                    )
                }

                is CutoutResult.Failure -> {
                    BackgroundRemovalOutcome(
                        cutoutFile = null,
                        cutoutBitmap = null,
                        status = BackgroundRemovalStatus.FAILED_KEPT_ORIGINAL,
                        confidence = null,
                        thumbnailSource = workingBitmap,
                    )
                }
            }

        private fun writeThumbnail(
            source: Bitmap,
            stagingDir: File,
        ): File {
            val thumbnailBitmap = ImageResizer.resizeToLongEdge(source, THUMBNAIL_SIZE_PX)
            val thumbnailFile = fileStore.fileFor(stagingDir, ImageType.THUMBNAIL)
            writeOrThrow(ProcessingStage.GENERATING_THUMBNAIL) {
                thumbnailFile.outputStream().use {
                    ImageResizer.encodeWebpLossy(
                        thumbnailBitmap,
                        THUMBNAIL_QUALITY,
                        it,
                    )
                }
            }
            return thumbnailFile
        }

        private fun buildVariants(
            originalFile: File,
            originalBitmap: Bitmap,
            outcome: BackgroundRemovalOutcome,
            thumbnailFile: File,
        ): List<ImageVariant> =
            buildList {
                add(originalFile.toVariant(ImageType.ORIGINAL, originalBitmap.width, originalBitmap.height))
                val cutoutFile = outcome.cutoutFile
                val cutoutBitmap = outcome.cutoutBitmap
                if (cutoutFile != null && cutoutBitmap != null) {
                    add(cutoutFile.toVariant(ImageType.CUTOUT, cutoutBitmap.width, cutoutBitmap.height))
                }
                val thumbnailBitmap = BitmapFactory.decodeFile(thumbnailFile.path)
                add(
                    thumbnailFile.toVariant(
                        ImageType.THUMBNAIL,
                        thumbnailBitmap?.width ?: 0,
                        thumbnailBitmap?.height ?: 0,
                    ),
                )
            }

        private fun validateOrThrow(
            file: File,
            stage: ProcessingStage,
        ) {
            when (val result = ImageValidator.validate(file)) {
                is ValidationResult.Invalid -> throw ImageProcessingException(stage, IOException(result.reason))
                ValidationResult.Valid -> Unit
            }
        }

        private inline fun writeOrThrow(
            stage: ProcessingStage,
            block: () -> Unit,
        ) {
            try {
                block()
            } catch (io: IOException) {
                throw ImageProcessingException(stage, io)
            }
        }

        private fun File.toVariant(
            type: ImageType,
            width: Int,
            height: Int,
        ): ImageVariant =
            ImageVariant(
                type = type,
                filePath = path,
                width = width,
                height = height,
                fileSizeBytes = length(),
                format = if (type == ImageType.ORIGINAL) "jpg" else "webp",
                checksum = ImageHasher.sha256(this),
            )

        /** Decodes with `inSampleSize` computed from a bounds-only pre-decode, so
         * a full-resolution camera image is never fully decoded into memory just
         * to be downscaled afterward (phase-5b-image-pipeline.md's performance
         * section). `OutOfMemoryError` is caught narrowly around this single
         * allocation, not as a blanket `catch (Throwable)`. */
        private fun decodeCapped(
            file: File,
            targetLongEdge: Int,
        ): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val longEdge = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (longEdge / (sampleSize * 2) >= targetLongEdge) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded =
                try {
                    BitmapFactory.decodeFile(file.path, options)
                } catch (oom: OutOfMemoryError) {
                    throw ImageProcessingException(ProcessingStage.SAVING_ORIGINAL, IOException("out_of_memory", oom))
                }
            return decoded ?: throw ImageProcessingException(
                ProcessingStage.SAVING_ORIGINAL,
                IOException("decodeFile returned null for ${file.path}"),
            )
        }

        private companion object {
            const val ORIGINAL_LONG_EDGE_PX = 2048
            const val ORIGINAL_JPEG_QUALITY = 85
            const val THUMBNAIL_SIZE_PX = 300
            const val THUMBNAIL_QUALITY = 80
            const val QUICK_CHECK_LONG_EDGE_PX = 512
        }
    }
