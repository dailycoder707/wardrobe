package com.wardrobe.app.core.tryon.pose

import android.graphics.Bitmap

/**
 * ADR-008's swappable-abstraction pattern, applied to Phase 10's body-landmark
 * estimation — modeled directly on
 * [com.wardrobe.app.core.image.segmentation.BackgroundRemover]: a best-effort,
 * always-overridable seed for [com.wardrobe.app.core.tryon.placement.DefaultPlacementCalculator],
 * never load-bearing. Failure or low confidence never blocks try-on rendering,
 * it only degrades the auto-placed default to a fixed heuristic the user can
 * freely drag away from.
 */
interface BodyAnchorEstimator {
    suspend fun estimate(bitmap: Bitmap): BodyAnchorEstimate
}

/**
 * The body-proportion fractions [com.wardrobe.app.core.tryon.pose.MlKitBodyAnchorEstimator]
 * can derive from a single reference photo's pose landmarks — the same
 * fields (and 0..1, image-size-independent convention) as
 * [com.wardrobe.app.core.model.tryon.BodyMeasurements], minus the
 * `bodyProfileId`/`source`/`computedAt` bookkeeping only the repository layer
 * knows how to fill in.
 */
sealed interface BodyAnchorEstimate {
    /** [confidence] is a real average of ML Kit's own per-landmark
     * `inFrameLikelihood` values — never a fabricated number (Constitution
     * rule 4). Any individual fraction field may still be `null` if its
     * underlying landmark pair wasn't confidently detected. */
    data class Success(
        val shoulderWidthFraction: Float?,
        val torsoHeightFraction: Float?,
        val waistHeightFraction: Float?,
        val hipWidthFraction: Float?,
        val neckPositionYFraction: Float?,
        val anklePositionYFraction: Float?,
        val confidence: Float,
    ) : BodyAnchorEstimate

    data class Failure(
        val reason: String,
    ) : BodyAnchorEstimate
}
