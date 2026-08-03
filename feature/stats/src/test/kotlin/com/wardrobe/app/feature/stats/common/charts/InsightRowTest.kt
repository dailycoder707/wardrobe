package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.feature.stats.common.InsightItemUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InsightRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val item =
        InsightItemUiModel(id = 1L, isOutfit = false, thumbnailPath = null, title = "Blazer", metric = "Worn 3 times")

    @Test
    fun `renders title and metric`() {
        composeTestRule.setContent { WardrobeTheme { InsightRow(item = item, onClick = {}) } }

        composeTestRule.onNodeWithText("Blazer").assertExists()
        composeTestRule.onNodeWithText("Worn 3 times").assertExists()
    }

    @Test
    fun `tapping the row invokes onClick`() {
        var clicked = false
        composeTestRule.setContent { WardrobeTheme { InsightRow(item = item, onClick = { clicked = true }) } }

        composeTestRule.onNodeWithText("Blazer").performClick()

        assert(clicked)
    }
}
