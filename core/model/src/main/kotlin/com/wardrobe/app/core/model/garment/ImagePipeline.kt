package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataSuggestion

/** Where a captured/imported photo came from — lets a future UI (Phase 5c) label
 * or log which affordance the user used. The pipeline itself (`core:image`)
 * behaves identically regardless of source; nothing here is persisted to
 * `image_metadata` (Phase 3's schema has no such column, and no query needs one).
 * [SHARE] and [DRAG_AND_DROP] have no wired entry point yet — see
 * phase-5b-image-pipeline.md's "explicitly out of scope" section. */
enum class ImageImportSource { CAMERA, GALLERY, SHARE, DRAG_AND_DROP }

/** M10's one-tap retry actions, keyed so `ImageRepository.retryStage` stays
 * a single method rather than three near-identical ones. */
enum class ImageRetryStage { EXTRACTION, ENHANCEMENT, METADATA }

/** One stage of `GarmentImagePipeline` (`core:image`), in execution order. Used
 * both as the pipeline's own internal progress callback and mapped to a
 * `WorkManager.setProgress` percentage by `ImageProcessingWorker` (`core:data`).
 *
 * [EXTRACTING_GARMENT] through [GENERATING_METADATA] are Add-to-Wardrobe v2's
 * (ADR-012) canonical AI-capability order — Extraction → Enhancement →
 * Reconstruction → Metadata, the user's own explicit ordering (Enhancement
 * runs *before* Reconstruction, not after) — replacing v1's single
 * `REMOVING_BACKGROUND` stage. Each of the three engine-backed stages
 * degrades gracefully to "skipped" when extraction itself failed; there is
 * nothing to enhance/reconstruct/describe without a cutout.
 */
enum class ProcessingStage {
    VALIDATING,
    CORRECTING_ORIENTATION,
    SAVING_ORIGINAL,
    ANALYZING_QUALITY,
    CROPPING,
    EXTRACTING_GARMENT,
    ENHANCING_PRESENTATION,
    RECONSTRUCTING_OCCLUSIONS,
    GENERATING_METADATA,
    GENERATING_THUMBNAIL,
    HASHING,
    DONE,
}

/** A crop rectangle in 0..1 normalized coordinates, independent of the source
 * bitmap's actual pixel dimensions — the eventual crop-gesture UI (Phase 5c)
 * can compute this from any preview size. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "NormalizedRect coordinates must be in 0..1: $this"
        }
        require(left < right && top < bottom) { "NormalizedRect must have positive area: $this" }
    }
}

enum class QualityVerdict { PASS, WARNING, FAIL }

enum class QualityCheckName { RESOLUTION, SHARPNESS, BRIGHTNESS, EXPOSURE, FRAMING, BACKGROUND_COMPLEXITY }

/** One check from the Photo Quality Assistant (`ImageQualityAnalyzer`,
 * `core:image`) — see phase-5b-image-pipeline.md for what each [name] measures. */
data class QualityCheck(
    val name: QualityCheckName,
    val verdict: QualityVerdict,
    val message: String,
)

/** The Photo Quality Assistant's full result for one photo. [overall] is the
 * worst individual verdict, but this never blocks continuing — the user can
 * always proceed anyway (Constitution rule 8: never a dead end). */
data class QualityReport(
    val checks: List<QualityCheck>,
) {
    val overall: QualityVerdict
        get() =
            when {
                checks.any { it.verdict == QualityVerdict.FAIL } -> QualityVerdict.FAIL
                checks.any { it.verdict == QualityVerdict.WARNING } -> QualityVerdict.WARNING
                else -> QualityVerdict.PASS
            }
}

/** Whether `GarmentExtractionEngine` (`core:image`) actually produced a
 * garment-only cutout for a given staged image — v1's name survives v2's
 * extraction-engine rewrite (garment-only isolation, not just background
 * removal) since the three values still mean the same thing to every
 * existing caller. [FAILED_KEPT_ORIGINAL] is not an error state for the
 * pipeline as a whole — the original/cropped image is still usable, just
 * without a cutout variant (Constitution rule 8). */
enum class BackgroundRemovalStatus { SUCCEEDED, FAILED_KEPT_ORIGINAL, SKIPPED }

/** Occlusion-reconstruction's outcome for one staged image — mirrors
 * `ReconstructionResult` (`core:image`) but as plain `core:model` data so
 * `StagedImage` can carry it without depending on `core:image`.
 * [RECONSTRUCTED] only ever happens above the reconstruction engine's own
 * `ConfidenceTier.HIGH` bar (`core:data`'s `GarmentReconstructionEngineRouter`);
 * [NOT_ATTEMPTED] is the honest default whenever no occlusion was detected,
 * no cloud provider is configured, or the fill's confidence wasn't high
 * enough to trust — never a silently guessed fill. */
enum class ReconstructionOutcome { RECONSTRUCTED, NOT_ATTEMPTED }

/** One stage of Add-to-Wardrobe v2's review-time "what did AI change" viewer
 * (M10) — [ComparisonStage] entries are built only for stages that actually
 * ran; a garment with no occlusion reconstruction simply has no
 * [RECONSTRUCTED] entry, never a fabricated one. Backed by files inside the
 * staging directory that are *not* one of [ImageType]'s permanent variants —
 * `GarmentImagePipeline`'s own temp comparison files, cleaned up along with
 * the rest of the staging directory on commit/discard, never copied into a
 * garment's permanent storage. */
enum class ComparisonStageLabel { ORIGINAL, EXTRACTED, ENHANCED, RECONSTRUCTED }

data class ComparisonStage(
    val label: ComparisonStageLabel,
    val filePath: String,
)

/** The Settings-style "AI Processing Complete" summary card's data (M10) —
 * summarizes the metadata-generation call specifically, since that's the
 * stage whose output the review screen actually displays. `null` on
 * [StagedImage] whenever there's no cutout to describe (extraction failed).
 * [processingMs] is real, measured wall-clock time for the whole
 * Extraction→Enhancement→Reconstruction→Metadata block, not an estimate. */
data class AiProcessingSummary(
    val source: AiResultSource,
    val provider: String?,
    val averageConfidence: Float?,
    val processingMs: Long,
    val cacheHit: Boolean,
    /** What Settings asked for, when that differs from [source] — a cloud
     * capability whose dispatch failed still produces a usable on-device
     * result, and the review screen has to be able to say so rather than
     * silently presenting it as the user's configured choice. Defaults to
     * [source] (no fallback) so existing call sites are unchanged. */
    val requestedSource: AiResultSource = source,
    /** The safe, human-readable reason the cloud attempt failed — `null`
     * whenever no fallback happened. Never carries credentials. */
    val fallbackReason: String? = null,
) {
    val fallbackUsed: Boolean get() = requestedSource != source
}

/** One not-yet-persisted image file produced by the pipeline — the pre-commit
 * counterpart of [ImageMetadata], which is what this becomes once
 * `ImageRepository.commitStagedImage` writes it to Room. */
data class ImageVariant(
    val type: ImageType,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: String,
    val checksum: String,
)

/** The full output of `GarmentImagePipeline.process` for one photo, keyed by a
 * client-generated [stagingId] (a UUID string) rather than a `GarmentId`, since
 * this exists before the garment it belongs to has been saved — see
 * `ImageRepository.commitStagedImage`/`discardStagedImage`.
 *
 * [reconstructionOutcome]/[occlusionSeverity] and [metadataSuggestions] are
 * Add-to-Wardrobe v2 additions (ADR-012): [occlusionSeverity] is only
 * meaningful when [reconstructionOutcome] is [ReconstructionOutcome.NOT_ATTEMPTED]
 * (the review screen's cue to offer a retake), and [metadataSuggestions] is
 * always empty when [backgroundRemovalStatus] isn't
 * [BackgroundRemovalStatus.SUCCEEDED] — there is no cutout to describe.
 * [comparisonStages] and [aiProcessingSummary] back M10's review-screen
 * "what did AI change"/"AI Processing Complete" UI.
 */
data class StagedImage(
    val stagingId: String,
    val variants: List<ImageVariant>,
    val qualityReport: QualityReport,
    val backgroundRemovalStatus: BackgroundRemovalStatus,
    val cutoutConfidence: Float?,
    val reconstructionOutcome: ReconstructionOutcome,
    val occlusionSeverity: Float?,
    val metadataSuggestions: List<MetadataSuggestion>,
    val comparisonStages: List<ComparisonStage> = emptyList(),
    val aiProcessingSummary: AiProcessingSummary? = null,
    /** M25 real-device finding — `null` whenever extraction failed entirely
     * (nothing to report provenance for) or on a build predating this
     * field. When present and `fallbackUsed`, a cloud-configured
     * `GARMENT_EXTRACTION` dispatch degraded to on-device for this image;
     * the review screen uses this to say so rather than presenting the
     * on-device cutout as the user's configured cloud choice. */
    val extractionProvenance: AiResultProvenance? = null,
)
