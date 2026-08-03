package com.wardrobe.app.feature.outfits.detail

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
class OutfitDetailTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping Try On invokes onTryOn`() {
        var tryOnCalled = false
        val state = OutfitDetailTopBarState(title = "Weekend Look", isFavorite = false, isArchived = false)
        val actions =
            OutfitDetailActions(
                onBack = {},
                onToggleFavorite = {},
                onRestyle = {},
                onTryOn = { tryOnCalled = true },
                onDuplicate = {},
                onArchiveToggle = {},
                onDeleteRequest = {},
            )

        composeRule.setContent {
            WardrobeTheme { OutfitDetailTopBar(state = state, actions = actions) }
        }

        composeRule.onNodeWithContentDescription("Try On").performClick()

        assertTrue(tryOnCalled)
    }
}
