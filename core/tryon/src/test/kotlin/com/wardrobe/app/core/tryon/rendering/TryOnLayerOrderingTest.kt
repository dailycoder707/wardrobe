package com.wardrobe.app.core.tryon.rendering

import com.wardrobe.app.core.model.outfit.OutfitSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class TryOnLayerOrderingTest {
    @Test
    fun `outerwear renders after a normal-depth top regardless of list order`() {
        val items = listOf(OutfitSlot.OUTERWEAR, OutfitSlot.TOP)

        val sorted = sortForRender(items) { it }

        assertEquals(listOf(OutfitSlot.TOP, OutfitSlot.OUTERWEAR), sorted)
    }

    @Test
    fun `same-depth slots keep OutfitSlot declaration order`() {
        val items = listOf(OutfitSlot.SHOES, OutfitSlot.TOP, OutfitSlot.BOTTOM)

        val sorted = sortForRender(items) { it }

        assertEquals(listOf(OutfitSlot.TOP, OutfitSlot.BOTTOM, OutfitSlot.SHOES), sorted)
    }

    @Test
    fun `outerwear stays last even when it appears first in the input list`() {
        val items = listOf(OutfitSlot.OUTERWEAR, OutfitSlot.BOTTOM, OutfitSlot.TOP, OutfitSlot.SHOES)

        val sorted = sortForRender(items) { it }

        assertEquals(OutfitSlot.OUTERWEAR, sorted.last())
    }
}
