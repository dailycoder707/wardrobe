package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.garment.DressCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosetFilterPresetsTest {
    @Test
    fun `every preset maps to a non-empty, real DressCode set`() {
        ClosetFilterPreset.entries.forEach { preset ->
            assertTrue("${preset.name} must have at least one dress code", preset.dressCodes.isNotEmpty())
        }
    }

    @Test
    fun `presets reuse the same business-athletic-casual categorization Recommendations already uses`() {
        assertEquals(setOf(DressCode.BUSINESS, DressCode.SMART_CASUAL), ClosetFilterPreset.WORK.dressCodes)
        assertEquals(setOf(DressCode.CASUAL), ClosetFilterPreset.CASUAL.dressCodes)
        assertEquals(setOf(DressCode.SMART_CASUAL), ClosetFilterPreset.DATE_NIGHT.dressCodes)
        assertEquals(setOf(DressCode.ATHLETIC), ClosetFilterPreset.ATHLETIC.dressCodes)
    }

    @Test
    fun `applying a preset is just an ordinary, fully overridable dress code filter`() {
        val applied = ClosetFilterState.EMPTY.copy(dressCodes = ClosetFilterPreset.ATHLETIC.dressCodes)

        assertEquals(1, applied.activeCount)
        assertEquals(ClosetFilterState.EMPTY, applied.copy(dressCodes = emptySet()))
    }
}
