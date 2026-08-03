package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarChartTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders one bar per entry with its label and value`() {
        val entries = listOf(BarChartEntry("Spring", 3), BarChartEntry("Summer", 9), BarChartEntry("Autumn", 1))

        composeTestRule.setContent { WardrobeTheme { BarChart(entries) } }

        composeTestRule.onNodeWithText("Spring").assertExists()
        composeTestRule.onNodeWithText("Summer").assertExists()
        composeTestRule.onNodeWithText("9").assertExists()
    }

    @Test
    fun `an empty entry list renders nothing rather than crashing`() {
        composeTestRule.setContent { WardrobeTheme { BarChart(emptyList()) } }

        composeTestRule.onNodeWithText("0").assertDoesNotExist()
    }
}
