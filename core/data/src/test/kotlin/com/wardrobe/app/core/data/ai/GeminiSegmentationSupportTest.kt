package com.wardrobe.app.core.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiSegmentationSupportTest {
    @Test
    fun `parses a well-formed single detection`() {
        val raw =
            """{"detections": [{"label": "blue dress", "box_2d": [100, 200, 800, 900], "mask": "iVBORw0KGgo="}]}"""

        val detection = parseGeminiSegmentationDetection(raw)

        assertEquals(listOf(100, 200, 800, 900), detection?.box2d)
        assertEquals("iVBORw0KGgo=", detection?.mask)
        assertEquals("blue dress", detection?.label)
    }

    @Test
    fun `strips the data URI prefix defensively by leaving it for the compositor to strip`() {
        val raw = """{"detections": [{"box_2d": [0, 0, 1000, 1000], "mask": "data:image/png;base64,iVBORw0KGgo="}]}"""

        val detection = parseGeminiSegmentationDetection(raw)

        assertTrue(detection?.mask?.startsWith("data:") == true)
    }

    @Test
    fun `returns null for an empty detections list rather than guessing`() {
        val raw = """{"detections": []}"""

        assertNull(parseGeminiSegmentationDetection(raw))
    }

    @Test
    fun `returns null for malformed JSON rather than throwing`() {
        assertNull(parseGeminiSegmentationDetection("not json at all {{{"))
    }

    @Test
    fun `returns null when box_2d does not have exactly 4 elements`() {
        val raw = """{"detections": [{"box_2d": [0, 0, 1000], "mask": "iVBORw0KGgo="}]}"""

        assertNull(parseGeminiSegmentationDetection(raw))
    }

    @Test
    fun `returns null when the mask is missing`() {
        val raw = """{"detections": [{"box_2d": [0, 0, 1000, 1000]}]}"""

        assertNull(parseGeminiSegmentationDetection(raw))
    }

    @Test
    fun `returns null when the mask is blank`() {
        val raw = """{"detections": [{"box_2d": [0, 0, 1000, 1000], "mask": "   "}]}"""

        assertNull(parseGeminiSegmentationDetection(raw))
    }

    @Test
    fun `returns null when the detections key is missing entirely`() {
        assertNull(parseGeminiSegmentationDetection("""{"unexpected": true}"""))
    }

    @Test
    fun `system prompt asks for exactly one detection of the primary garment, never a guess`() {
        assertTrue("detections" in GEMINI_SEGMENTATION_SYSTEM_PROMPT)
        assertTrue("box_2d" in GEMINI_SEGMENTATION_SYSTEM_PROMPT)
        assertTrue("guessing" in GEMINI_SEGMENTATION_SYSTEM_PROMPT)
    }
}
