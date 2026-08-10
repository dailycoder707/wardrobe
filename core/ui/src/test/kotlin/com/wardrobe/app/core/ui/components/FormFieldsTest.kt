package com.wardrobe.app.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormFieldsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `helperText is absent by default, matching every pre-existing call site`() {
        composeRule.setContent {
            WardrobeTheme {
                DropdownField(label = "Brand", options = listOf(1 to "Levi's"), selected = 1, onSelect = {})
            }
        }

        composeRule.onNodeWithText("AI suggested").assertDoesNotExist()
    }

    @Test
    fun `helperText renders when supplied, e_g_ an AI-suggested-value caption`() {
        composeRule.setContent {
            WardrobeTheme {
                DropdownField(
                    label = "Brand",
                    options = listOf(1 to "Levi's"),
                    selected = 1,
                    onSelect = {},
                    helperText = "AI suggested",
                )
            }
        }

        composeRule.onNodeWithText("AI suggested").assertExists()
    }
}
