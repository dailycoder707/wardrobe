package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImageMetadata
import com.wardrobe.app.core.model.garment.NormalizedRect
import com.wardrobe.app.core.model.garment.ProcessingStage
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.StagedImage
import kotlinx.coroutines.flow.Flow

/** Mirrors `BackupProgress`/`RestoreProgress`'s shape — a WorkManager-backed
 * job surfaced as a `Flow`, not a second progress pattern invented for images. */
sealed interface ImageProcessingProgress {
    data class InProgress(
        val stage: ProcessingStage,
    ) : ImageProcessingProgress

    data class Completed(
        val staged: StagedImage,
    ) : ImageProcessingProgress

    data class Failed(
        val reason: String,
    ) : ImageProcessingProgress
}

/**
 * The seam Phase 5c's capture/review ViewModels will call — nothing calls this
 * yet (Phase 5b implements the pipeline, not the screens). See
 * phase-5b-image-pipeline.md for the full stage → commit/discard lifecycle.
 */
interface ImageRepository {
    /** Fast, no file writes — the Photo Quality Assistant's result, available
     * before the (comparatively expensive) full pipeline run below. */
    suspend fun analyzeQuality(sourceFilePath: String): QualityReport

    /** Runs the full pipeline (`core:image`'s `GarmentImagePipeline`) as a
     * WorkManager job keyed by a caller-supplied `stagingId`, so a batch import
     * is simply N calls, no special batch path. */
    fun stageImage(
        sourceFilePath: String,
        stagingId: String,
        cropRect: NormalizedRect? = null,
    ): Flow<ImageProcessingProgress>

    /** Moves the staged files into their permanent, garmentId-addressed home
     * and inserts the corresponding `image_metadata` rows. */
    suspend fun commitStagedImage(
        stagingId: String,
        garmentId: GarmentId,
    ): List<ImageMetadata>

    /** The user rejected this capture — deletes the staging directory, no DB
     * row was ever written for it. */
    suspend fun discardStagedImage(stagingId: String)

    /** Read-only lookup of a still-staged (not yet committed) result — the
     * Add-to-Wardrobe review screen's preview image. Same known limitation
     * as `StagedImageStore`'s own KDoc: in-memory only, so a process death
     * between staging finishing and the review screen opening loses it
     * (returns `null`); the caller is expected to fall back to restaging
     * rather than crash. */
    suspend fun peekStagedImage(stagingId: String): StagedImage?

    /** Exact-file duplicate detection for the Add-to-Wardrobe review screen —
     * a byte-identical re-import of a photo already saved against some
     * garment. Backed by [ImageMetadata]'s own SHA-256 `checksum`, already
     * computed by the pipeline into every [StagedImage] variant; this just
     * exposes the existing `ImageMetadataDao.getByChecksum` lookup (used
     * today only by sync handlers) through the domain layer. Never a
     * perceptual/visual similarity match — only a byte-for-byte one. */
    suspend fun findGarmentIdForChecksum(checksum: String): GarmentId?

    fun observeImages(garmentId: GarmentId): Flow<List<ImageMetadata>>

    /** Deletes this garment's image files from disk. Room's own `CASCADE`
     * already removes the `image_metadata` rows when the garment row is
     * deleted — this only handles the files, which nothing else will. */
    suspend fun deleteImagesForGarment(garmentId: GarmentId)
}
