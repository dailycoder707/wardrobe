package com.wardrobe.app.feature.closet.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import com.wardrobe.app.feature.closet.closet.ClosetGrid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClosetGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val garments =
        listOf(
            GarmentTileUiModel(1L, null, "Blue Shirt", null, false, false),
            GarmentTileUiModel(2L, null, "Red Dress", null, true, false),
        )

    @Test
    fun `renders every garment tile`() {
        composeRule.setContent {
            WardrobeTheme {
                ClosetGrid(
                    garments = garments,
                    columnCount = 2,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onColumnCountChange = {},
                    onOpenGarment = {},
                    onToggleSelection = {},
                    onEnterSelectionMode = {},
                    onFavoriteClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Blue Shirt").assertExists()
        composeRule.onNodeWithText("Red Dress").assertExists()
    }

    @Test
    fun `tapping a tile opens that garment`() {
        var openedId: Long? = null
        composeRule.setContent {
            WardrobeTheme {
                ClosetGrid(
                    garments = garments,
                    columnCount = 2,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onColumnCountChange = {},
                    onOpenGarment = { openedId = it },
                    onToggleSelection = {},
                    onEnterSelectionMode = {},
                    onFavoriteClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Blue Shirt").performClick()
        assert(openedId == 1L)
    }

    @Test
    fun `long-pressing a tile enters selection mode`() {
        var enteredSelectionId: Long? = null
        composeRule.setContent {
            WardrobeTheme {
                ClosetGrid(
                    garments = garments,
                    columnCount = 2,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onColumnCountChange = {},
                    onOpenGarment = {},
                    onToggleSelection = {},
                    onEnterSelectionMode = { enteredSelectionId = it },
                    onFavoriteClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Blue Shirt").performTouchInput { longClick() }
        assert(enteredSelectionId == 1L)
    }
}
