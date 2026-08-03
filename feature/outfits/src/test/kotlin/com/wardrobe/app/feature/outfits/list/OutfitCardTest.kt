package com.wardrobe.app.feature.outfits.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutfitCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val outfit =
        OutfitCardUiModel(
            id = 1L,
            title = "Weekend Look",
            thumbnailPaths = emptyList(),
            isFavorite = false,
            occasionName = "Casual",
            wearCount = 3,
        )

    @Test
    fun `renders title and occasion`() {
        composeTestRule.setContent {
            WardrobeTheme {
                OutfitCard(outfit = outfit, onClick = {}, onLongClick = {}, onFavoriteClick = {}, onTryOnClick = {})
            }
        }

        composeTestRule.onNodeWithText("Weekend Look").assertExists()
        composeTestRule.onNodeWithText("Casual").assertExists()
    }

    @Test
    fun `tapping the card invokes onClick`() {
        var clicked = false
        composeTestRule.setContent {
            WardrobeTheme {
                OutfitCard(
                    outfit = outfit,
                    onClick = { clicked = true },
                    onLongClick = {},
                    onFavoriteClick = {},
                    onTryOnClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Weekend Look").performClick()

        assert(clicked)
    }

    @Test
    fun `tapping favorite invokes onFavoriteClick without triggering onClick`() {
        var favoriteClicked = false
        var cardClicked = false
        composeTestRule.setContent {
            WardrobeTheme {
                OutfitCard(
                    outfit = outfit,
                    onClick = { cardClicked = true },
                    onLongClick = {},
                    onFavoriteClick = { favoriteClicked = true },
                    onTryOnClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Favorite Weekend Look").performClick()

        assert(favoriteClicked)
        assert(!cardClicked)
    }

    @Test
    fun `tapping try on invokes onTryOnClick without triggering onClick`() {
        var tryOnClicked = false
        var cardClicked = false
        composeTestRule.setContent {
            WardrobeTheme {
                OutfitCard(
                    outfit = outfit,
                    onClick = { cardClicked = true },
                    onLongClick = {},
                    onFavoriteClick = {},
                    onTryOnClick = { tryOnClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Try on Weekend Look").performClick()

        assert(tryOnClicked)
        assert(!cardClicked)
    }
}
