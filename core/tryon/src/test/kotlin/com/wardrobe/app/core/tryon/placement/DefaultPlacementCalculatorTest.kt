package com.wardrobe.app.core.tryon.placement

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.model.tryon.TryOnAnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private fun measurements(
    shoulderWidthFraction: Float? = null,
    neckPositionYFraction: Float? = null,
    waistHeightFraction: Float? = null,
    anklePositionYFraction: Float? = null,
    source: MeasurementSource = MeasurementSource.POSE_DETECTION,
) = BodyMeasurements(
    bodyProfileId = BodyProfileId(1),
    shoulderWidthFraction = shoulderWidthFraction,
    torsoHeightFraction = null,
    waistHeightFraction = waistHeightFraction,
    hipWidthFraction = null,
    neckPositionYFraction = neckPositionYFraction,
    anklePositionYFraction = anklePositionYFraction,
    confidence = 0.9f,
    source = source,
    computedAt = Instant.EPOCH,
)

class DefaultPlacementCalculatorTest {
    @Test
    fun `no measurements falls back to the fixed heuristic and is reported as a heuristic`() {
        val result = DefaultPlacementCalculator.calculate(TryOnAnchorRegion.SHOULDER_LINE, measurements = null)

        assertEquals(0.5f, result.offsetXFraction)
        assertEquals(0.22f, result.offsetYFraction)
        assertEquals(1f, result.scale)
        assertEquals(MeasurementSource.DEFAULT_HEURISTIC, result.placementSource)
    }

    @Test
    fun `full real measurements are used and reported as pose detection`() {
        val result =
            DefaultPlacementCalculator.calculate(
                TryOnAnchorRegion.SHOULDER_LINE,
                measurements(shoulderWidthFraction = 0.28f, neckPositionYFraction = 0.2f),
            )

        assertEquals(0.2f, result.offsetYFraction)
        assertEquals(1f, result.scale, 0.001f)
        assertEquals(MeasurementSource.POSE_DETECTION, result.placementSource)
    }

    @Test
    fun `a partially missing measurement downgrades the whole result to heuristic`() {
        val result =
            DefaultPlacementCalculator.calculate(
                TryOnAnchorRegion.SHOULDER_LINE,
                measurements(shoulderWidthFraction = null, neckPositionYFraction = 0.2f),
            )

        assertEquals(MeasurementSource.DEFAULT_HEURISTIC, result.placementSource)
    }

    @Test
    fun `waist line reads waistHeightFraction, not neckPositionYFraction`() {
        val result =
            DefaultPlacementCalculator.calculate(
                TryOnAnchorRegion.WAIST_LINE,
                measurements(waistHeightFraction = 0.47f, neckPositionYFraction = 0.2f),
            )

        assertEquals(0.47f, result.offsetYFraction)
    }

    @Test
    fun `hip line has no measured field so it always uses the fallback`() {
        val result =
            DefaultPlacementCalculator.calculate(
                TryOnAnchorRegion.HIP_LINE,
                measurements(waistHeightFraction = 0.47f, shoulderWidthFraction = 0.28f),
            )

        assertEquals(0.50f, result.offsetYFraction)
        assertEquals(MeasurementSource.DEFAULT_HEURISTIC, result.placementSource)
    }

    @Test
    fun `a wider than reference shoulder measurement scales up proportionally`() {
        val result =
            DefaultPlacementCalculator.calculate(
                TryOnAnchorRegion.SHOULDER_LINE,
                measurements(shoulderWidthFraction = 0.35f, neckPositionYFraction = 0.2f),
            )

        assertEquals(0.35f / 0.28f, result.scale, 0.001f)
    }
}
