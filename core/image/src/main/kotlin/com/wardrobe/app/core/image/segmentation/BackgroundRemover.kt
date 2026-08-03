package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap

/**
 * ADR-008's swappable abstraction, unchanged from its Phase 1 design. See
 * phase-5b-image-pipeline.md's "Background removal" section for which
 * implementation is bound this phase and the honest caveat about what hasn't
 * been empirically verified yet.
 */
interface BackgroundRemover {
    suspend fun removeBackground(bitmap: Bitmap): CutoutResult
}

sealed interface CutoutResult {
    /** [confidence] is `null` when the implementation doesn't expose a real
     * scalar confidence — never fabricated (Constitution rule 4). */
    data class Success(
        val bitmap: Bitmap,
        val confidence: Float?,
    ) : CutoutResult

    data class Failure(
        val reason: String,
    ) : CutoutResult
}
