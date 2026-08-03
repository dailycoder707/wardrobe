package com.wardrobe.app.core.tryon.placement

import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.model.tryon.TryOnAnchorRegion

private const val DEFAULT_OFFSET_X = 0.5f
private const val DEFAULT_SCALE = 1f
private const val DEFAULT_ROTATION = 0f

/** A garment cutout whose measured shoulder width exactly matches this
 * fraction renders at [DEFAULT_SCALE] — a reasoned reference value (roughly
 * a typical shoulder-width fraction of a full-body photo), not a spike-
 * measured one; see `phase-10-personal-virtual-tryon.md`'s Known
 * Limitations. Any other measured width scales proportionally from it. */
private const val REFERENCE_SHOULDER_WIDTH_FRACTION = 0.28f

/** Fixed heuristic vertical placement per anchor region, used whenever the
 * corresponding [BodyMeasurements] field is unavailable (no profile yet, or
 * pose detection couldn't resolve that landmark pair) — a full-body photo,
 * 0 (top) to 1 (bottom), 0.5 horizontal center always assumed. Each entry's
 * own [TryOnAnchorRegion] key already documents what its literal means, so a
 * `MagicNumber` suppression here is more honest than inventing nine named
 * constants no clearer than the map itself. */
@Suppress("MagicNumber")
private val FALLBACK_OFFSET_Y =
    mapOf(
        TryOnAnchorRegion.SHOULDER_LINE to 0.22f,
        TryOnAnchorRegion.NECK to 0.16f,
        TryOnAnchorRegion.WAIST_LINE to 0.46f,
        TryOnAnchorRegion.HIP_LINE to 0.50f,
        TryOnAnchorRegion.FULL_TORSO to 0.35f,
        TryOnAnchorRegion.WRIST to 0.55f,
        TryOnAnchorRegion.FINGER to 0.58f,
        TryOnAnchorRegion.EARLOBE to 0.10f,
        TryOnAnchorRegion.ANKLE to 0.92f,
    )

/**
 * Pure geometry — [TryOnAnchorRegion] plus whatever [BodyMeasurements]
 * fractions actually resolved → the seed values for a `DEFAULT`
 * [com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate]. Never a warp
 * target, only ever the starting translate/scale for an affine layer the
 * user can immediately drag/pinch/rotate away from (Constitution rule 7).
 *
 * [BodyMeasurements.waistHeightFraction] is read as the waist line's
 * vertical position (fraction of photo height from the top), the same
 * explicit-Y-position convention [BodyMeasurements.neckPositionYFraction]/
 * [BodyMeasurements.anklePositionYFraction] use — [HIP_LINE]/[FULL_TORSO]/
 * [WRIST]/[FINGER]/[EARLOBE] have no corresponding measured field in this
 * schema at all, so they always use [FALLBACK_OFFSET_Y] (a real, disclosed
 * gap, not a guessed measurement).
 *
 * [CalculatedPlacement.placementSource] is only ever
 * [MeasurementSource.POSE_DETECTION] when *every* value used (offset and
 * scale) came from a real measurement — one fallback anywhere downgrades
 * the whole result to [MeasurementSource.DEFAULT_HEURISTIC], never reported
 * as more confident than it is (Constitution rule 4).
 */
object DefaultPlacementCalculator {
    data class CalculatedPlacement(
        val offsetXFraction: Float,
        val offsetYFraction: Float,
        val scale: Float,
        val rotationDegrees: Float,
        val placementSource: MeasurementSource,
    )

    fun calculate(
        region: TryOnAnchorRegion,
        measurements: BodyMeasurements?,
    ): CalculatedPlacement {
        val measuredOffsetY = measuredOffsetY(region, measurements)
        val measuredShoulderWidth = measurements?.shoulderWidthFraction
        val usedFallback = measuredOffsetY == null || measuredShoulderWidth == null
        return CalculatedPlacement(
            offsetXFraction = DEFAULT_OFFSET_X,
            offsetYFraction = measuredOffsetY ?: FALLBACK_OFFSET_Y.getValue(region),
            scale = measuredShoulderWidth?.let { it / REFERENCE_SHOULDER_WIDTH_FRACTION } ?: DEFAULT_SCALE,
            rotationDegrees = DEFAULT_ROTATION,
            placementSource =
                if (usedFallback) {
                    MeasurementSource.DEFAULT_HEURISTIC
                } else {
                    requireNotNull(measurements).source
                },
        )
    }

    private fun measuredOffsetY(
        region: TryOnAnchorRegion,
        measurements: BodyMeasurements?,
    ): Float? =
        when (region) {
            TryOnAnchorRegion.SHOULDER_LINE, TryOnAnchorRegion.NECK -> measurements?.neckPositionYFraction

            TryOnAnchorRegion.WAIST_LINE -> measurements?.waistHeightFraction

            TryOnAnchorRegion.ANKLE -> measurements?.anklePositionYFraction

            TryOnAnchorRegion.HIP_LINE,
            TryOnAnchorRegion.FULL_TORSO,
            TryOnAnchorRegion.WRIST,
            TryOnAnchorRegion.FINGER,
            TryOnAnchorRegion.EARLOBE,
            -> null
        }
}
