package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AiStyledBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun cloudStyledModel() =
        RecommendedOutfitUiModel(
            outfit =
                Outfit(
                    id = OutfitId(1),
                    name = null,
                    garments = emptyList(),
                    occasionId = null,
                    source = OutfitSource.AI_SUGGESTED,
                    isSaved = false,
                    photoUri = null,
                    createdAt = Instant.EPOCH,
                ),
            explanation = "A breezy weekend look",
            reasonBullets = listOf("A breezy weekend look"),
            score = 12.0,
            items = emptyList(),
            provenance = AiResultProvenance(AiResultSource.CLOUD, "OpenAI", "gpt-vision", "styling-v1", Instant.EPOCH),
            aiConfidence = 0.87f,
        )

    @Test
    fun `the AI Styled badge shows provider and confidence behind its info button`() {
        composeRule.setContent {
            WardrobeTheme { AiStyledBadge(cloudStyledModel()) }
        }

        composeRule.onNodeWithText("AI Styled").assertExists()
        composeRule.onNodeWithContentDescription("Why did AI suggest this outfit?").performClick()

        composeRule.onNodeWithText("Provider: OpenAI").assertExists()
        composeRule.onNodeWithText("Confidence: 87%").assertExists()
        composeRule.onNodeWithText("Reason: A breezy weekend look").assertExists()
    }
}
