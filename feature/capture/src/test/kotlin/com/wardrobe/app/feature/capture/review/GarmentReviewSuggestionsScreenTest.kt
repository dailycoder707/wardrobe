package com.wardrobe.app.feature.capture.review

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.AiProcessingSummary
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.QualityCheck
import com.wardrobe.app.core.model.garment.QualityCheckName
import com.wardrobe.app.core.model.garment.QualityVerdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Total bindable fields per `MetadataSuggestionResolver.isBindableField` —
 * kept as one named constant here so the "how many missing rows" assertion
 * below doesn't hardcode a magic number disconnected from that set. */
private const val BINDABLE_FIELD_COUNT = 18

private fun emptyReviewReferenceData() =
    ReviewReferenceData(
        categories = emptyList(),
        brands = emptyList(),
        colors = emptyList(),
        materials = emptyList(),
        fabrics = emptyList(),
        occasions = emptyList(),
        tags = emptyList(),
    )

@RunWith(RobolectricTestRunner::class)
class GarmentReviewSuggestionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun suggestion(
        field: MetadataField,
        value: String,
        confidence: Float?,
    ) = MetadataSuggestion(
        field,
        value,
        confidence,
        AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
    )

    @Test
    fun `a MEDIUM suggestion chip shows its confidence tier and reports a toggle tap`() {
        var toggled: Pair<MetadataField, String>? = null
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = listOf(suggestion(MetadataField.CATEGORY, "Tops", 0.6f)),
                    form = GarmentMetadataFormState(),
                    reference = emptyReviewReferenceData(),
                    onToggleSuggestion = { field, value -> toggled = field to value },
                )
            }
        }

        composeRule.onNodeWithText("Medium").assertExists()
        composeRule.onNodeWithText("Category: Tops").performClick()

        assertEquals(MetadataField.CATEGORY to "Tops", toggled)
    }

    @Test
    fun `a bindable field with no suggestion at all shows Unknown, please choose`() {
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = listOf(suggestion(MetadataField.CATEGORY, "Tops", 0.9f)),
                    form = GarmentMetadataFormState(),
                    reference = emptyReviewReferenceData(),
                    onToggleSuggestion = { _, _ -> },
                )
            }
        }

        // AI Wardrobe Assistant Part 3: every bindable field nothing suggested
        // gets an explicit row — not just the three original M10 fields.
        composeRule.onNodeWithText("Material: Unknown").assertExists()
        composeRule.onNodeWithText("Fabric: Unknown").assertExists()
        composeRule.onNodeWithText("Pattern: Unknown").assertExists()
        composeRule.onAllNodesWithText("Please choose").assertCountEquals(BINDABLE_FIELD_COUNT - 1)
    }

    @Test
    fun `a field that doesn't apply to this item's category shows N slash A, not Unknown`() {
        val topsCategory = Category(CategoryId(1), "Tops", null, CategoryLevel.TOP)
        val shoesCategory = Category(CategoryId(2), "Shoes", null, CategoryLevel.TOP)
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = listOf(suggestion(MetadataField.CATEGORY, "Shoes", 0.9f)),
                    form = GarmentMetadataFormState(categoryId = shoesCategory.id),
                    reference = emptyReviewReferenceData().copy(categories = listOf(topsCategory, shoesCategory)),
                    onToggleSuggestion = { _, _ -> },
                )
            }
        }

        // Shoes have no fit/neckline/sleeve length — N/A, never "Unknown".
        composeRule.onNodeWithText("Fit: N/A").assertExists()
        composeRule.onNodeWithText("Neckline: N/A").assertExists()
        composeRule.onNodeWithText("Sleeve Length: N/A").assertExists()
    }

    @Test
    fun `a field the on-device provider can't detect at all shows Not supported, not Unknown`() {
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = listOf(suggestion(MetadataField.PRIMARY_COLOR, "Blue", 0.9f)),
                    form = GarmentMetadataFormState(),
                    reference = emptyReviewReferenceData(),
                    onToggleSuggestion = { _, _ -> },
                    source = AiResultSource.ON_DEVICE,
                )
            }
        }

        // M23: Material/Fabric are never on-device-supported — this is not
        // the same "please choose" a genuinely undetected field gets.
        composeRule.onNodeWithText("Material: Not supported by On-Device AI").assertExists()
        composeRule.onNodeWithText("Fabric: Not supported by On-Device AI").assertExists()
        // Every NOT_SUPPORTED field shares the same caption — just prove it
        // rendered at all rather than asserting an exact, fragile count.
        composeRule.onAllNodesWithText("Enable Cloud AI in Settings for full detection").onFirst().assertExists()
    }

    @Test
    fun `a field the on-device provider does support still shows Unknown when genuinely undetected this time`() {
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = emptyList(),
                    form = GarmentMetadataFormState(),
                    reference = emptyReviewReferenceData(),
                    onToggleSuggestion = { _, _ -> },
                    source = AiResultSource.ON_DEVICE,
                )
            }
        }

        // Pattern and Brand are genuinely on-device-supported (see
        // MetadataFieldSupport) — a missing suggestion for them is a real
        // "AI couldn't tell this time", not a capability gap.
        composeRule.onNodeWithText("Pattern: Unknown").assertExists()
        composeRule.onNodeWithText("Brand: Unknown").assertExists()
    }

    @Test
    fun `a HIGH-confidence suggestion that fails reference resolution is flagged, not silently unapplied`() {
        composeRule.setContent {
            WardrobeTheme {
                MetadataSuggestionsSection(
                    suggestions = listOf(suggestion(MetadataField.PRIMARY_COLOR, "Chartreuse", 0.95f)),
                    form = GarmentMetadataFormState(),
                    reference = emptyReviewReferenceData(),
                    onToggleSuggestion = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Detected, but no matching option found — choose manually").assertExists()
    }

    @Test
    fun `the AI status card never shows a fabricated cache or confidence value`() {
        composeRule.setContent {
            WardrobeTheme {
                AiStatusCard(
                    AiProcessingSummary(
                        source = AiResultSource.ON_DEVICE,
                        provider = null,
                        averageConfidence = null,
                        processingMs = 1800L,
                        cacheHit = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Provider: On-Device").assertExists()
        composeRule.onNodeWithText("Processing: 1.8s").assertExists()
        composeRule.onNodeWithText("Cache: No").assertExists()
    }

    @Test
    fun `quality warnings show the retake recommendation`() {
        composeRule.setContent {
            WardrobeTheme {
                QualityWarningBanner(
                    listOf(QualityCheck(QualityCheckName.SHARPNESS, QualityVerdict.WARNING, "Photo looks blurry")),
                )
            }
        }

        composeRule.onNodeWithText("• Photo looks blurry").assertExists()
        composeRule.onNodeWithText("Consider retaking this photo for the best result.").assertExists()
    }
}
