package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wardrobe.app.core.model.garment.ComparisonStage
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.StagedImage
import java.io.File
import java.io.IOException

/*
 * M10's one-tap retry actions — split out of GarmentImagePipeline itself
 * (Detekt's TooManyFunctions) as extension functions using the class's
 * internal-visibility engine properties. Each redoes only the stages
 * downstream of the one being retried, reusing files the original
 * GarmentImagePipeline.process run already wrote to the staging
 * directory — no recapture needed. `previous` supplies every field that
 * doesn't change (stagingId, quality report, and whichever upstream
 * results weren't retried).
 *
 * Known, disclosed limitation: retryExtraction re-decodes the saved
 * ORIGINAL file without reapplying any crop rectangle the original
 * capture used — the review screen doesn't expose re-cropping, so this
 * never surfaces as a user-visible regression today, but a future crop
 * editor would need to persist the crop rect to retry it faithfully.
 */

/** Redoes Extraction → Enhancement → Reconstruction → Metadata from the
 * saved original photo. */
suspend fun GarmentImagePipeline.retryExtraction(
    previous: StagedImage,
    onProgress: suspend (ProcessingStage) -> Unit = {},
): StagedImage {
    val stagingDir = fileStore.stagingDir(previous.stagingId)
    val originalFile = fileStore.fileFor(stagingDir, ImageType.ORIGINAL)
    check(originalFile.exists()) { "No original photo staged for ${previous.stagingId}" }
    val originalBitmap = decodeCapped(originalFile, GarmentImagePipeline.ORIGINAL_LONG_EDGE_PX)

    onProgress(ProcessingStage.EXTRACTING_GARMENT)
    val extraction = extractGarment(extractionEngine, originalBitmap, onProgress)
    val enhancement = enhancePresentation(presentationEnhancer, extraction, onProgress)
    val reconstruction = reconstructOcclusions(reconstructionEngine, enhancement, extraction.confidence, onProgress)
    val metadataSuggestions = generateMetadata(metadataEngine, reconstruction.finalCutout, onProgress)

    val originalVariant = previous.variants.first { it.type == ImageType.ORIGINAL }
    val stageOutputs = PipelineStageOutputs(extraction, enhancement, reconstruction)
    val finalized = finalizeRetry(stagingDir, originalFile, originalVariant, originalBitmap, stageOutputs)
    return previous.copy(
        backgroundRemovalStatus = extraction.status,
        cutoutConfidence = extraction.confidence,
        reconstructionOutcome = reconstruction.outcome,
        occlusionSeverity = reconstruction.occlusionSeverity,
        metadataSuggestions = metadataSuggestions,
        variants = finalized.variants,
        comparisonStages = finalized.comparisonStages,
        aiProcessingSummary = buildAiProcessingSummary(metadataSuggestions, finalized.elapsedMs),
    )
}

/** Redoes Enhancement → Reconstruction → Metadata from the raw extracted
 * cutout the original run cached — extraction itself is not repeated, so
 * this never re-dispatches a (possibly billed) cloud extraction call. */
suspend fun GarmentImagePipeline.retryEnhancement(
    previous: StagedImage,
    onProgress: suspend (ProcessingStage) -> Unit = {},
): StagedImage {
    val stagingDir = fileStore.stagingDir(previous.stagingId)
    val extractedFile = comparisonFileFor(stagingDir, ComparisonStageLabel.EXTRACTED)
    check(extractedFile.exists()) { "No cached extraction to re-enhance for ${previous.stagingId}" }
    val extractedBitmap = decodeOrThrow(extractedFile)
    val extraction = ExtractionOutcome(extractedBitmap, previous.backgroundRemovalStatus, previous.cutoutConfidence)

    val enhancement = enhancePresentation(presentationEnhancer, extraction, onProgress)
    val reconstruction = reconstructOcclusions(reconstructionEngine, enhancement, previous.cutoutConfidence, onProgress)
    val metadataSuggestions = generateMetadata(metadataEngine, reconstruction.finalCutout, onProgress)

    val originalFile = fileStore.fileFor(stagingDir, ImageType.ORIGINAL)
    val originalVariant = previous.variants.first { it.type == ImageType.ORIGINAL }
    val stageOutputs = PipelineStageOutputs(extraction, enhancement, reconstruction)
    val finalized = finalizeRetry(stagingDir, originalFile, originalVariant, extractedBitmap, stageOutputs)
    return previous.copy(
        reconstructionOutcome = reconstruction.outcome,
        occlusionSeverity = reconstruction.occlusionSeverity,
        metadataSuggestions = metadataSuggestions,
        variants = finalized.variants,
        comparisonStages = finalized.comparisonStages,
        aiProcessingSummary = buildAiProcessingSummary(metadataSuggestions, finalized.elapsedMs),
    )
}

/** Redoes only Metadata generation from the already-final cutout — no
 * image file changes at all. */
suspend fun GarmentImagePipeline.retryMetadata(
    previous: StagedImage,
    onProgress: suspend (ProcessingStage) -> Unit = {},
): StagedImage {
    val stagingDir = fileStore.stagingDir(previous.stagingId)
    val cutoutFile = fileStore.fileFor(stagingDir, ImageType.CUTOUT)
    check(cutoutFile.exists()) { "No cutout available to retry metadata for ${previous.stagingId}" }
    val cutout = decodeOrThrow(cutoutFile)

    val startedAt = System.currentTimeMillis()
    val metadataSuggestions = generateMetadata(metadataEngine, cutout, onProgress)
    val elapsedMs = System.currentTimeMillis() - startedAt
    return previous.copy(
        metadataSuggestions = metadataSuggestions,
        aiProcessingSummary = buildAiProcessingSummary(metadataSuggestions, elapsedMs),
    )
}

private class FinalizedRetry(
    val variants: List<ImageVariant>,
    val comparisonStages: List<ComparisonStage>,
    val elapsedMs: Long,
)

private fun GarmentImagePipeline.finalizeRetry(
    stagingDir: File,
    originalFile: File,
    originalVariant: ImageVariant,
    thumbnailFallbackSource: Bitmap,
    stageOutputs: PipelineStageOutputs,
): FinalizedRetry {
    val startedAt = System.currentTimeMillis()
    val thumbnailSource = stageOutputs.reconstruction.finalCutout ?: thumbnailFallbackSource
    val thumbnailFile = writeThumbnail(fileStore, thumbnailSource, stagingDir)
    val variants =
        buildVariants(
            fileStore,
            originalVariant,
            stageOutputs.reconstruction,
            stageOutputs.enhancement,
            thumbnailFile,
            stagingDir,
        )
    val comparisonStages = buildComparisonStages(fileStore, stagingDir, originalFile, stageOutputs, variants)
    return FinalizedRetry(variants, comparisonStages, System.currentTimeMillis() - startedAt)
}

private fun decodeOrThrow(file: File): Bitmap =
    BitmapFactory.decodeFile(file.path) ?: throw ImageProcessingException(
        ProcessingStage.HASHING,
        IOException("decodeFile returned null for ${file.path}"),
    )
