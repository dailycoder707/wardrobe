package com.wardrobe.app.feature.closet.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendationPreviewCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tile(id: Long) =
        GarmentTileUiModel(
            id = id,
            thumbnailPath = null,
            title = "Item $id",
            subtitle = null,
            isFavorite = false,
            isRecentlyAdded = false,
        )

    @Test
    fun `tapping Try On Me invokes onTryOn without opening recommendations`() {
        var tryOnCalled = false
        var recommendationsOpened = false
        val recommendation =
            RecommendationPreviewUiModel(items = listOf(tile(1), tile(2)), explanation = "It's mild today")

        composeRule.setContent {
            WardrobeTheme {
                RecommendationPreviewCard(
                    recommendation = recommendation,
                    onOpenGarment = {},
                    onOpenRecommendations = { recommendationsOpened = true },
                    onTryOn = { tryOnCalled = true },
                )
            }
        }

        composeRule.onNodeWithText("Try On Me").performClick()

        assertEquals(true, tryOnCalled)
        assertEquals(false, recommendationsOpened)
    }
}
