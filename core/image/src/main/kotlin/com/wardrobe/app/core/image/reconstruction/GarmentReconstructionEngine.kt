package com.wardrobe.app.core.image.reconstruction

import android.graphics.Bitmap

/**
 * Add-to-Wardrobe v2's occlusion-reconstruction capability — fills garment
 * regions hidden behind an arm/hand/hair/pose without ever inventing color,
 * pattern, or fabric texture (Constitution rule 4's "never fabricate," and
 * the user's own explicit requirement that a low-confidence occlusion must
 * become a retake prompt or manual edit, never a guessed fill). No on-device
 * inpainting model exists in this stack, so [OnDeviceReconstructionEngine]
 * always returns [ReconstructionResult.NotAttempted] — only a configured
 * cloud provider (`core:ai`'s `CloudReconstructionEngine`) can actually fill
 * a region, and only above its own confidence threshold.
 */
interface GarmentReconstructionEngine {
    suspend fun reconstruct(
        cutout: Bitmap,
        extractionConfidence: Float?,
    ): ReconstructionResult
}

sealed interface ReconstructionResult {
    data class Reconstructed(
        val bitmap: Bitmap,
        val confidence: Float,
    ) : ReconstructionResult

    /** [occlusionSeverity] is a 0..1 estimate of how much of the garment's
     * expected silhouette is missing (alpha-channel holes/notches near the
     * cutout's own bounds) — used by the review screen to decide whether to
     * prompt a retake. `0f` means no occlusion was detected at all. */
    data class NotAttempted(
        val occlusionSeverity: Float,
        val reason: String,
    ) : ReconstructionResult
}
