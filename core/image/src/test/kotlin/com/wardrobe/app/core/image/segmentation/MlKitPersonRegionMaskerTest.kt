package com.wardrobe.app.core.image.segmentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHOULDER_WIDTH = 200f
private const val NOSE_X = 500f
private const val NOSE_Y = 300f

/**
 * M25 real-device finding: the head punch-out circle used to have a radius
 * of `0.55 * shoulderWidth`, centered on the nose — large enough that its
 * lower edge reached well past the chin into the collar/neckline/upper
 * chest of the actual garment, showing up as "garment regions missing" on
 * a real device. These tests pin the corrected geometry directly, without
 * needing to construct ML Kit's own `Pose`/`PoseLandmark` types.
 */
class MlKitPersonRegionMaskerTest {
    @Test
    fun `head circle radius is a real fraction of shoulder width, not larger than a third`() {
        val circle = computeHeadCircle(NOSE_X, NOSE_Y, SHOULDER_WIDTH)

        assertTrue("radius should be well under half the shoulder width", circle.radius < SHOULDER_WIDTH / 2)
        assertEquals(SHOULDER_WIDTH * 0.30f, circle.radius, 0.01f)
    }

    @Test
    fun `head circle center is lifted above the nose landmark, not centered on it`() {
        val circle = computeHeadCircle(NOSE_X, NOSE_Y, SHOULDER_WIDTH)

        assertTrue("center should be strictly above the nose y-coordinate", circle.cy < NOSE_Y)
    }

    @Test
    fun `head circle's lower edge no longer reaches a full radius below the nose`() {
        // The old geometry (radius centered exactly on the nose) reached
        // NOSE_Y + radius at its lowest point. The corrected geometry must
        // reach less far down for the same radius, since the center is
        // lifted above the nose.
        val circle = computeHeadCircle(NOSE_X, NOSE_Y, SHOULDER_WIDTH)
        val lowestPoint = circle.cy + circle.radius
        val oldLowestPoint = NOSE_Y + SHOULDER_WIDTH * 0.55f

        assertTrue(
            "corrected circle's lowest point ($lowestPoint) should sit well above the old geometry's " +
                "($oldLowestPoint)",
            lowestPoint < oldLowestPoint,
        )
    }

    @Test
    fun `head circle stays centered on the landmark's x-coordinate`() {
        val circle = computeHeadCircle(NOSE_X, NOSE_Y, SHOULDER_WIDTH)

        assertEquals(NOSE_X, circle.cx, 0.01f)
    }
}
