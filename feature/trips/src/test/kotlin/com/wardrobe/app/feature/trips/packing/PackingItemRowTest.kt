package com.wardrobe.app.feature.trips.packing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PackingItemRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun item(garmentId: Long?) =
        PackingItemUiModel(id = 1L, garmentId = garmentId, name = "Blue Shirt", isPacked = false, rationale = null)

    @Test
    fun `a garment-backed item shows a Try On action`() {
        composeRule.setContent {
            WardrobeTheme { PackingItemRow(item = item(garmentId = 5L), onToggle = {}, onTryOn = {}) }
        }

        composeRule.onNodeWithContentDescription("Try on Blue Shirt").assertExists()
    }

    @Test
    fun `a free-text item (no garment) shows no Try On action`() {
        composeRule.setContent {
            WardrobeTheme { PackingItemRow(item = item(garmentId = null), onToggle = {}, onTryOn = null) }
        }

        composeRule.onNodeWithContentDescription("Try on Blue Shirt").assertDoesNotExist()
    }

    @Test
    fun `tapping Try On invokes the callback`() {
        var tapped = false
        composeRule.setContent {
            WardrobeTheme { PackingItemRow(item = item(garmentId = 5L), onToggle = {}, onTryOn = { tapped = true }) }
        }

        composeRule.onNodeWithContentDescription("Try on Blue Shirt").performClick()

        assertTrue(tapped)
    }
}
