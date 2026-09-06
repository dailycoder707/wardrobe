package com.wardrobe.app.feature.capture.review

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.garment.Brand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** M22 — Part 4's "AI-generated vs. user-entered information is
 * distinguishable" requirement: [GarmentReviewDropdowns]/
 * [GarmentReviewGarmentAttributeFields] show a small "AI suggested" caption
 * on a field whose *current* value matches a real [MetadataSuggestion],
 * reusing [isSuggestionApplied] rather than a second, invented notion of
 * "applied." */
@RunWith(RobolectricTestRunner::class)
class GarmentReviewMetadataFieldsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val leviId = BrandId(1)
    private val reference =
        ReviewReferenceData(
            categories = emptyList(),
            brands = listOf(Brand(leviId, "Levi's", null)),
            colors = emptyList(),
            materials = emptyList(),
            fabrics = emptyList(),
            occasions = emptyList(),
            tags = emptyList(),
        )

    private fun brandSuggestion() =
        MetadataSuggestion(
            field = MetadataField.BRAND,
            value = "Levi's",
            confidence = 0.9f,
            provenance = AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
        )

    @Test
    fun `a field whose value matches a real suggestion shows the AI-suggested caption`() {
        val state =
            GarmentReviewMetadataUiState(
                form = GarmentMetadataFormState(brandId = leviId),
                brands = reference.brands,
                metadataSuggestions = listOf(brandSuggestion()),
            )
        composeRule.setContent {
            WardrobeTheme {
                GarmentReviewDropdowns(state = state, form = state.form, onFormChange = {}, reference = reference)
            }
        }

        composeRule.onNodeWithText("AI suggested").assertExists()
    }

    @Test
    fun `a field the user changed away from the suggestion shows no AI-suggested caption`() {
        val otherId = BrandId(2)
        val state =
            GarmentReviewMetadataUiState(
                form = GarmentMetadataFormState(brandId = otherId),
                brands = reference.brands + Brand(otherId, "Zara", null),
                metadataSuggestions = listOf(brandSuggestion()),
            )
        composeRule.setContent {
            WardrobeTheme {
                GarmentReviewDropdowns(
                    state = state,
                    form = state.form,
                    onFormChange = {},
                    reference = reference.copy(brands = state.brands),
                )
            }
        }

        composeRule.onNodeWithText("AI suggested").assertDoesNotExist()
    }

    @Test
    fun `no reference data means no AI-suggested captions are computed at all`() {
        val state =
            GarmentReviewMetadataUiState(
                form = GarmentMetadataFormState(brandId = leviId),
                brands = reference.brands,
                metadataSuggestions = listOf(brandSuggestion()),
            )
        composeRule.setContent {
            WardrobeTheme {
                GarmentReviewDropdowns(state = state, form = state.form, onFormChange = {})
            }
        }

        composeRule.onNodeWithText("AI suggested").assertDoesNotExist()
    }
}
