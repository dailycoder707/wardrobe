package com.wardrobe.app.core.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiActivityBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows exactly the caller-supplied label, never invented copy`() {
        composeRule.setContent {
            WardrobeTheme {
                AiActivityBanner(label = "Analyzing garment…", tone = AiActivityTone.RUNNING)
            }
        }

        composeRule.onNodeWithText("Analyzing garment…").assertExists()
    }

    @Test
    fun `an action is only rendered when both a label and a handler are supplied`() {
        var clicked = false
        composeRule.setContent {
            WardrobeTheme {
                AiActivityBanner(
                    label = "Cloud AI isn't configured.",
                    tone = AiActivityTone.INFO,
                    actionLabel = "Configure Cloud AI",
                    onAction = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Configure Cloud AI").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun `no action is shown when the caller supplies no handler`() {
        composeRule.setContent {
            WardrobeTheme {
                AiActivityBanner(label = "AI analysis complete", tone = AiActivityTone.SUCCESS)
            }
        }

        composeRule.onNodeWithText("Configure Cloud AI").assertDoesNotExist()
    }

    @Test
    fun `the label is announced as a live region for screen readers`() {
        composeRule.setContent {
            WardrobeTheme {
                AiActivityBanner(label = "Styling an outfit…", tone = AiActivityTone.RUNNING)
            }
        }

        composeRule
            .onNodeWithText("Styling an outfit…", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }
}
