package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.common.BodyProfileId
import java.time.Instant

/** Where a [BodyMeasurements] value came from — the confidence/source pair
 * is Constitution rule 7's "editable suggestion with a confidence signal,
 * never a fact" made concrete: a caller can always tell whether a number
 * came from real on-device pose estimation or a fallback heuristic, and
 * how confident the estimator itself was. */
enum class MeasurementSource {
    POSE_DETECTION,
    DEFAULT_HEURISTIC,
}

/**
 * Derived body proportions, expressed as fractions of the [BodyPose.NEUTRAL]
 * reference photo's own width/height (the same 0..1, image-size-independent
 * convention `NormalizedRect` already uses elsewhere in this schema) —
 * **persisted separately from [BodyProfile]**, in its own table, and only
 * recomputed when the profile's photos actually change
 * (`BodyProfileRepository.recomputeMeasurements()`), never per render. Kept
 * as a distinct entity specifically so "don't recompute on every render" is
 * a structural fact (a row with its own [computedAt], not a column that
 * could silently go stale inside [BodyProfile] itself).
 *
 * All fields nullable: pose detection (or its fallback heuristic) may not
 * be able to determine every measurement from every photo set, and an
 * absent value must never be presented as a guessed one.
 */
data class BodyMeasurements(
    val bodyProfileId: BodyProfileId,
    val shoulderWidthFraction: Float?,
    val torsoHeightFraction: Float?,
    val waistHeightFraction: Float?,
    val hipWidthFraction: Float?,
    val neckPositionYFraction: Float?,
    val anklePositionYFraction: Float?,
    val confidence: Float?,
    val source: MeasurementSource,
    val computedAt: Instant,
)
