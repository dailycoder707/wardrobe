package com.wardrobe.app.core.model.garment

/** Where a captured/imported photo came from — lets a future UI (Phase 5c) label
 * or log which affordance the user used. The pipeline itself (`core:image`)
 * behaves identically regardless of source; nothing here is persisted to
 * `image_metadata` (Phase 3's schema has no such column, and no query needs one).
 * [SHARE] and [DRAG_AND_DROP] have no wired entry point yet — see
 * phase-5b-image-pipeline.md's "explicitly out of scope" section. */
enum class ImageImportSource { CAMERA, GALLERY, SHARE, DRAG_AND_DROP }

/** One stage of `GarmentImagePipeline` (`core:image`), in execution order. Used
 * both as the pipeline's own internal progress callback and mapped to a
 * `WorkManager.setProgress` percentage by `ImageProcessingWorker` (`core:data`). */
enum class ProcessingStage {
    VALIDATING,
    CORRECTING_ORIENTATION,
    SAVING_ORIGINAL,
    ANALYZING_QUALITY,
    CROPPING,
    REMOVING_BACKGROUND,
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

/** Whether `BackgroundRemover` (`core:image`) actually produced a cutout for a
 * given staged image. [FAILED_KEPT_ORIGINAL] is not an error state for the
 * pipeline as a whole — the original/cropped image is still usable, just
 * without a cutout variant (Constitution rule 8). */
enum class BackgroundRemovalStatus { SUCCEEDED, FAILED_KEPT_ORIGINAL, SKIPPED }

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
 * `ImageRepository.commitStagedImage`/`discardStagedImage`. */
data class StagedImage(
    val stagingId: String,
    val variants: List<ImageVariant>,
    val qualityReport: QualityReport,
    val backgroundRemovalStatus: BackgroundRemovalStatus,
    val cutoutConfidence: Float?,
)
