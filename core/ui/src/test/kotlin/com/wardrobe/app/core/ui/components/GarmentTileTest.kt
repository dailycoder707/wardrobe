package com.wardrobe.app.core.ui.components

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
class GarmentTileTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val garment =
        GarmentTileUiModel(
            id = 1L,
            thumbnailPath = null,
            title = "Cream Silk Blouse",
            subtitle = "Reformation",
            isFavorite = false,
            isRecentlyAdded = false,
        )

    @Test
    fun `displays title and subtitle`() {
        composeRule.setContent {
            WardrobeTheme {
                GarmentTile(garment = garment, isSelected = false, isSelectionMode = false, onClick = {
                }, onLongClick = {}, onFavoriteClick = {})
            }
        }

        composeRule.onNodeWithText("Cream Silk Blouse").assertExists()
        composeRule.onNodeWithText("Reformation").assertExists()
    }

    @Test
    fun `tapping the tile invokes onClick`() {
        var clicked = false
        composeRule.setContent {
            WardrobeTheme {
                GarmentTile(
                    garment = garment,
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = { clicked = true },
                    onLongClick = {},
                    onFavoriteClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Cream Silk Blouse").performClick()
        assert(clicked)
    }

    @Test
    fun `tapping the favorite star invokes onFavoriteClick`() {
        var favorited = false
        composeRule.setContent {
            WardrobeTheme {
                GarmentTile(
                    garment = garment,
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = {},
                    onLongClick = {},
                    onFavoriteClick = { favorited = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Favorite Cream Silk Blouse").performClick()
        assert(favorited)
    }

    @Test
    fun `favorited garment shows the remove-from-favorites description`() {
        composeRule.setContent {
            WardrobeTheme {
                GarmentTile(
                    garment = garment.copy(isFavorite = true),
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = {},
                    onLongClick = {},
                    onFavoriteClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Remove Cream Silk Blouse from favorites").assertExists()
    }
}
