package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.AiProcessingSummary
import com.wardrobe.app.core.model.garment.ComparisonStage
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImageVariant
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.ReconstructionOutcome
import java.io.File

private const val COMPARISON_QUALITY = 85

/** Bundles the three AI-capability stage outputs together purely to keep
 * [buildComparisonStages] under this project's `LongParameterList`
 * threshold — the same reasoning as `core:ai`'s `AiDispatchContext`. */
internal class PipelineStageOutputs(
    val extraction: ExtractionOutcome,
    val enhancement: PresentationOutcome,
    val reconstruction: ReconstructionOutput,
)

/** M10's review-time "what did AI change" viewer and "AI Processing
 * Complete" summary — split out of [GarmentImagePipeline] (Detekt's
 * `TooManyFunctions`). [buildComparisonStages] only ever adds a stage that
 * actually happened; an [ExtractionOutcome] with no cutout or a
 * [ReconstructionOutput] that was never attempted simply produces a shorter
 * list, never a fabricated entry. */
internal fun buildComparisonStages(
    fileStore: ImageFileStore,
    stagingDir: File,
    originalFile: File,
    outputs: PipelineStageOutputs,
    variants: List<ImageVariant>,
): List<ComparisonStage> =
    buildList {
        add(ComparisonStage(ComparisonStageLabel.ORIGINAL, originalFile.path))
        outputs.extraction.cutout?.let {
            add(comparisonStage(ComparisonStageLabel.EXTRACTED, fileStore, stagingDir, it))
        }
        outputs.enhancement.enhancedCutout?.let {
            add(comparisonStage(ComparisonStageLabel.ENHANCED, fileStore, stagingDir, it))
        }
        if (outputs.reconstruction.outcome == ReconstructionOutcome.RECONSTRUCTED) {
            variants.firstOrNull { it.type == ImageType.CUTOUT }?.let {
                add(ComparisonStage(ComparisonStageLabel.RECONSTRUCTED, it.filePath))
            }
        }
    }

/** The one place `compare_<label>.webp` filenames are spelled out — reused
 * by the M10 retry entry points (`GarmentImagePipelineRetry.kt`) to find a
 * previous run's cached extraction/enhancement output. */
internal fun comparisonFileFor(
    stagingDir: File,
    label: ComparisonStageLabel,
): File = File(stagingDir, "compare_${label.name.lowercase()}.webp")

private fun comparisonStage(
    label: ComparisonStageLabel,
    fileStore: ImageFileStore,
    stagingDir: File,
    bitmap: Bitmap,
): ComparisonStage {
    val file = comparisonFileFor(fileStore.ensureExists(stagingDir), label)
    writeOrThrow(ProcessingStage.HASHING) {
        file.outputStream().use { ImageResizer.encodeWebpLossy(bitmap, COMPARISON_QUALITY, it) }
    }
    return ComparisonStage(label, file.path)
}

/** `null` whenever there were no metadata suggestions to summarize
 * (extraction failed, so nothing downstream ran) — never a summary card
 * with fabricated provider/confidence values. */
internal fun buildAiProcessingSummary(
    metadataSuggestions: List<MetadataSuggestion>,
    processingMs: Long,
): AiProcessingSummary? {
    if (metadataSuggestions.isEmpty()) return null
    val provenances = metadataSuggestions.map { it.provenance }
    val cloudProvenance = provenances.firstOrNull { it.source == AiResultSource.CLOUD }
    val confidences = metadataSuggestions.mapNotNull { it.confidence }
    // A cloud attempt that failed leaves on-device suggestions carrying the
    // requested source and the reason (GarmentMetadataEngineRouter) — the
    // summary has to keep both, or the review screen presents a fallback as
    // if it were the configured provider.
    val fallback = provenances.firstOrNull { it.fallbackUsed }
    val actualSource = cloudProvenance?.source ?: AiResultSource.ON_DEVICE
    return AiProcessingSummary(
        source = actualSource,
        provider = cloudProvenance?.provider,
        averageConfidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        processingMs = processingMs,
        cacheHit = provenances.any { it.cacheHit },
        requestedSource = fallback?.requestedSource ?: actualSource,
        fallbackReason = fallback?.fallbackReason,
    )
}
