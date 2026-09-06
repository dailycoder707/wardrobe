package com.wardrobe.app.core.image.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [patternConfidence] as pure math — no `Bitmap`/ML Kit/Robolectric
 * needed, since it was pulled out to a top-level `internal` function
 * specifically for this. AI Wardrobe Assistant Parts 1-3: on-device Pattern
 * confidence used to be hardcoded `null`; this proves the replacement is a
 * real, varying function of the measured stddev, not a fixed value.
 */
class OnDeviceMetadataEngineTest {
    @Test
    fun `a stddev sitting exactly on the solid-vs-patterned threshold is maximally uncertain`() {
        assertEquals(0f, patternConfidence(28.0), 0.001f)
    }

    @Test
    fun `a stddev at or beyond the confidence margin below the threshold is maximally confident`() {
        assertEquals(1f, patternConfidence(0.0), 0.001f)
    }

    @Test
    fun `a stddev at or beyond the confidence margin above the threshold is maximally confident`() {
        assertEquals(1f, patternConfidence(56.0), 0.001f)
    }

    @Test
    fun `confidence is never negative or above 1, even for an extreme stddev`() {
        val extreme = patternConfidence(500.0)

        assertTrue(extreme in 0f..1f)
    }

    @Test
    fun `a stddev far from the threshold is more confident than one close to it, not a fixed number`() {
        val farFromThreshold = patternConfidence(2.0)
        val nearThreshold = patternConfidence(25.0)

        assertTrue(
            "a reading far from the threshold ($farFromThreshold) should be more confident than one near it ($nearThreshold)",
            farFromThreshold > nearThreshold,
        )
    }

    @Test
    fun `confidence scales continuously rather than snapping between fixed values`() {
        val closer = patternConfidence(20.0)
        val closest = patternConfidence(27.0)

        assertTrue(closer > closest)
        assertTrue(closest > 0f)
        assertTrue(closer < 1f)
    }
}
