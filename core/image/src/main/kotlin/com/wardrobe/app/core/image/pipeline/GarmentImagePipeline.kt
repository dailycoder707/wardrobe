package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.presentation.GarmentPresentationEnhancer
import com.wardrobe.app.core.image.quality.ImageQualityAnalyzer
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.image.validation.ImageValidator
import com.wardrobe.app.core.image.validation.ValidationResult
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates one photo through Add-to-Wardrobe v2's canonical stage order
 * (ADR-012 §9): Capture → Extraction → Enhancement → Reconstruction →
 * Metadata → (Review/Save happen above this layer). Every AI-capability
 * stage is driven through an on-device-default interface local to
 * `core:image` — [extractionEngine]/[reconstructionEngine]/[metadataEngine]
 * may each actually be a cloud-aware Router bound in `core:data`, but this
 * class never knows or cares; it only ever calls the interface. Returns
 * plain data ([StagedImage]) — this class has no Room dependency and never
 * writes an `image_metadata` row; that's `core:data`'s `ImageRepositoryImpl`
 * job, same layering Phase 5a established for every other repository.
 *
 * [retryExtraction]/[retryEnhancement]/[retryMetadata] (M10) let the review
 * screen redo a single downstream stage without recapturing — see
 * `GarmentImagePipelineRetry.kt`.
 */
@Singleton
class GarmentImagePipeline
    @Inject
    constructor(
        internal val fileStore: ImageFileStore,
        internal val extractionEngine: GarmentExtractionEngine,
        internal val presentationEnhancer: GarmentPresentationEnhancer,
        internal val reconstructionEngine: GarmentReconstructionEngine,
        internal val metadataEngine: GarmentMetadataEngine,
        private val qualityAnalyzer: ImageQualityAnalyzer,
    ) {
        /**
         * Fast path: decode capped at a small target size and run the quality
         * checks only — no file writes, no AI capability calls. Not `suspend`:
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
            val bitmap = decodeAndOrient(sourceFile)

            onProgress(ProcessingStage.SAVING_ORIGINAL)
            val stagingDir = fileStore.ensureExists(fileStore.stagingDir(stagingId))
            val originalFile = saveOriginal(bitmap, stagingDir)

            onProgress(ProcessingStage.ANALYZING_QUALITY)
            val qualityReport = qualityAnalyzer.analyze(bitmap)

            onProgress(ProcessingStage.CROPPING)
            val workingBitmap = if (cropRect != null) ImageCropper.crop(bitmap, cropRect) else bitmap

            val aiStagesStartedAt = System.currentTimeMillis()
            val extraction = extractGarment(extractionEngine, workingBitmap, onProgress)
            val enhancement = enhancePresentation(presentationEnhancer, extraction, onProgress)
            val reconstruction =
                reconstructOcclusions(reconstructionEngine, enhancement, extraction.confidence, onProgress)
            val metadataSuggestions = generateMetadata(metadataEngine, reconstruction.finalCutout, onProgress)
            val aiStagesElapsedMs = System.currentTimeMillis() - aiStagesStartedAt

            onProgress(ProcessingStage.GENERATING_THUMBNAIL)
            val thumbnailSource = reconstruction.finalCutout ?: workingBitmap
            val thumbnailFile = writeThumbnail(fileStore, thumbnailSource, stagingDir)

            onProgress(ProcessingStage.HASHING)
            val originalVariant = originalFile.toVariant(ImageType.ORIGINAL, bitmap.width, bitmap.height)
            val variants =
                buildVariants(fileStore, originalVariant, reconstruction, enhancement, thumbnailFile, stagingDir)
            val stageOutputs = PipelineStageOutputs(extraction, enhancement, reconstruction)
            val comparisonStages = buildComparisonStages(fileStore, stagingDir, originalFile, stageOutputs, variants)

            onProgress(ProcessingStage.DONE)
            return StagedImage(
                stagingId = stagingId,
                variants = variants,
                qualityReport = qualityReport,
                backgroundRemovalStatus = extraction.status,
                cutoutConfidence = extraction.confidence,
                reconstructionOutcome = reconstruction.outcome,
                occlusionSeverity = reconstruction.occlusionSeverity,
                metadataSuggestions = metadataSuggestions,
                comparisonStages = comparisonStages,
                aiProcessingSummary = buildAiProcessingSummary(metadataSuggestions, aiStagesElapsedMs),
                extractionProvenance = extraction.provenance,
            )
        }

        private fun decodeAndOrient(sourceFile: File): Bitmap {
            val orientation = ExifOrientation.readOrientation(sourceFile)
            val decoded = decodeCapped(sourceFile, ORIGINAL_LONG_EDGE_PX)
            val oriented = ExifOrientation.correct(decoded, orientation)
            return ImageResizer.resizeToLongEdge(oriented, ORIGINAL_LONG_EDGE_PX)
        }

        private fun saveOriginal(
            bitmap: Bitmap,
            stagingDir: File,
        ): File {
            val originalFile = fileStore.fileFor(stagingDir, ImageType.ORIGINAL)
            writeOrThrow(ProcessingStage.SAVING_ORIGINAL) {
                originalFile.outputStream().use { ImageResizer.encodeJpeg(bitmap, ORIGINAL_JPEG_QUALITY, it) }
            }
            return originalFile
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

        internal companion object {
            const val ORIGINAL_LONG_EDGE_PX = 2048
            const val ORIGINAL_JPEG_QUALITY = 85
            const val QUICK_CHECK_LONG_EDGE_PX = 512
        }
    }
