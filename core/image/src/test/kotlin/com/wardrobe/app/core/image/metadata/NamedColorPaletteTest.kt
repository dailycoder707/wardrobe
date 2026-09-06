package com.wardrobe.app.core.image.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedColorPaletteTest {
    @Test
    fun `nearest returns Red for a strongly red sample with high confidence`() {
        val (name, confidence) = NamedColorPalette.nearest(200, 30, 30)
        assertEquals("Red", name)
        assertEquals(1f, confidence)
    }

    @Test
    fun `nearest returns White for a near-white sample`() {
        val (name, _) = NamedColorPalette.nearest(245, 245, 245)
        assertEquals("White", name)
    }

    @Test
    fun `nearest returns Black for a near-black sample`() {
        val (name, _) = NamedColorPalette.nearest(20, 20, 20)
        assertEquals("Black", name)
    }

    @Test
    fun `nearest confidence is lower for a sample roughly equidistant between two entries`() {
        val (_, exactConfidence) = NamedColorPalette.nearest(200, 30, 30)
        val (_, ambiguousConfidence) = NamedColorPalette.nearest(150, 75, 30)
        assertTrue(
            "expected an ambiguous sample's confidence ($ambiguousConfidence) to be lower than an exact match's ($exactConfidence)",
            ambiguousConfidence < exactConfidence,
        )
    }
}
