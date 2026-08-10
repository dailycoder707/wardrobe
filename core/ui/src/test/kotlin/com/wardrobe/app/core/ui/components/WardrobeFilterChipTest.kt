package com.wardrobe.app.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CHECK_ICON_TEST_TAG = "wardrobe_filter_chip_check"

/** M22 — selected vs. unselected previously communicated only via
 * background fill/border for sighted users; a checkmark icon gives
 * low-vision users relying on shape, not color contrast, the same signal. */
@RunWith(RobolectricTestRunner::class)
class WardrobeFilterChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a selected chip renders a checkmark icon`() {
        composeRule.setContent {
            WardrobeTheme {
                WardrobeFilterChip(label = "Casual", selected = true, onClick = {})
            }
        }

        // The clickable chip merges its descendants' semantics into one
        // node for screen readers (by design — one coherent announcement,
        // not two separate ones) — an unmerged-tree lookup is needed to
        // find the decorative icon itself, distinct from that announcement.
        composeRule.onNodeWithTag(CHECK_ICON_TEST_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an unselected chip renders no checkmark icon`() {
        composeRule.setContent {
            WardrobeTheme {
                WardrobeFilterChip(label = "Casual", selected = false, onClick = {})
            }
        }

        composeRule.onNodeWithTag(CHECK_ICON_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an unselected chip still exposes real selection state to screen readers`() {
        composeRule.setContent {
            WardrobeTheme {
                WardrobeFilterChip(label = "Casual", selected = false, onClick = {})
            }
        }

        composeRule.onNodeWithContentDescription("Casual, not selected").assertExists()
    }
}
