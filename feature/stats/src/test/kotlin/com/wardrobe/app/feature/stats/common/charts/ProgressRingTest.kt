package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressRingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the center label as given`() {
        composeTestRule.setContent { WardrobeTheme { ProgressRing(progress = 0.62f, centerLabel = "62%") } }

        composeTestRule.onNodeWithText("62%").assertExists()
    }

    @Test
    fun `out-of-range progress is coerced rather than crashing`() {
        composeTestRule.setContent { WardrobeTheme { ProgressRing(progress = 1.5f, centerLabel = "150%") } }

        composeTestRule.onNodeWithText("150%").assertExists()
    }
}
