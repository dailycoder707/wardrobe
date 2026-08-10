package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.presentation.GarmentPresentationEnhancer
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.reconstruction.ReconstructionResult
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.ReconstructionOutcome

/**
 * The four Add-to-Wardrobe v2 AI-capability stages (ADR-012 §9) —
 * Extraction → Enhancement → Reconstruction → Metadata — split out of
 * [GarmentImagePipeline] itself (Detekt's `TooManyFunctions`) as plain
 * top-level functions taking the engine they call as a parameter, rather
 * than as private members reaching into `this`.
 */
internal class ExtractionOutcome(
    val cutout: Bitmap?,
    val status: BackgroundRemovalStatus,
    val confidence: Float?,
)

internal class PresentationOutcome(
    val enhancedCutout: Bitmap?,
    val whiteBackgroundVariant: Bitmap?,
)

internal class ReconstructionOutput(
    val finalCutout: Bitmap?,
    val outcome: ReconstructionOutcome,
    val occlusionSeverity: Float?,
)

internal suspend fun extractGarment(
    engine: GarmentExtractionEngine,
    workingBitmap: Bitmap,
    onProgress: suspend (ProcessingStage) -> Unit,
): ExtractionOutcome {
    onProgress(ProcessingStage.EXTRACTING_GARMENT)
    return when (val result = engine.extract(workingBitmap)) {
        is ExtractionResult.Success -> {
            ExtractionOutcome(result.transparentCutout, BackgroundRemovalStatus.SUCCEEDED, result.confidence)
        }

        is ExtractionResult.Failure -> {
            ExtractionOutcome(cutout = null, status = BackgroundRemovalStatus.FAILED_KEPT_ORIGINAL, confidence = null)
        }
    }
}

internal suspend fun enhancePresentation(
    enhancer: GarmentPresentationEnhancer,
    extraction: ExtractionOutcome,
    onProgress: suspend (ProcessingStage) -> Unit,
): PresentationOutcome {
    onProgress(ProcessingStage.ENHANCING_PRESENTATION)
    val cutout = extraction.cutout ?: return PresentationOutcome(null, null)
    val enhanced = enhancer.enhance(cutout)
    return PresentationOutcome(enhanced.enhancedCutout, enhanced.whiteBackgroundVariant)
}

internal suspend fun reconstructOcclusions(
    engine: GarmentReconstructionEngine,
    enhancement: PresentationOutcome,
    extractionConfidence: Float?,
    onProgress: suspend (ProcessingStage) -> Unit,
): ReconstructionOutput {
    onProgress(ProcessingStage.RECONSTRUCTING_OCCLUSIONS)
    val enhancedCutout =
        enhancement.enhancedCutout
            ?: return ReconstructionOutput(null, ReconstructionOutcome.NOT_ATTEMPTED, null)
    return when (val result = engine.reconstruct(enhancedCutout, extractionConfidence)) {
        is ReconstructionResult.Reconstructed -> {
            ReconstructionOutput(result.bitmap, ReconstructionOutcome.RECONSTRUCTED, occlusionSeverity = null)
        }

        is ReconstructionResult.NotAttempted -> {
            ReconstructionOutput(enhancedCutout, ReconstructionOutcome.NOT_ATTEMPTED, result.occlusionSeverity)
        }
    }
}

internal suspend fun generateMetadata(
    engine: GarmentMetadataEngine,
    finalCutout: Bitmap?,
    onProgress: suspend (ProcessingStage) -> Unit,
): List<MetadataSuggestion> {
    onProgress(ProcessingStage.GENERATING_METADATA)
    return finalCutout?.let { engine.generateMetadata(it) }.orEmpty()
}
