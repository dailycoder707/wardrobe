package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val topsId = CategoryId(1)
private val shoesId = CategoryId(2)

private val CATEGORIES =
    listOf(
        Category(topsId, "Tops", null, CategoryLevel.TOP),
        Category(shoesId, "Shoes", null, CategoryLevel.TOP),
    )

private val ALL_REQUIRED_FIELDS =
    listOf(
        MetadataField.CATEGORY,
        MetadataField.SUBCATEGORY,
        MetadataField.PRIMARY_COLOR,
        MetadataField.PATTERN,
        MetadataField.MATERIAL,
        MetadataField.FABRIC,
        MetadataField.FIT,
        MetadataField.DRESS_CODE,
        MetadataField.SEASON,
        MetadataField.OCCASION,
    )

private fun highConfidenceSuggestion(field: MetadataField) =
    MetadataSuggestion(
        field = field,
        value = "irrelevant",
        confidence = 0.95f,
        provenance = AiResultProvenance(AiResultSource.CLOUD, "openai", "gpt-vision", "metadata-v2", Instant.EPOCH),
    )

private fun mediumConfidenceSuggestion(field: MetadataField) =
    MetadataSuggestion(
        field = field,
        value = "irrelevant",
        confidence = 0.6f,
        provenance = AiResultProvenance(AiResultSource.CLOUD, "openai", "gpt-vision", "metadata-v2", Instant.EPOCH),
    )

class AutoSaveEligibilityTest {
    @Test
    fun `eligible when every required field is HIGH confidence`() {
        val suggestions = ALL_REQUIRED_FIELDS.map { highConfidenceSuggestion(it) }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertEquals(AutoSaveEligibility.Eligible, result)
    }

    @Test
    fun `eligible when Fit is HIGH-satisfied through Not-Applicable rather than a real suggestion`() {
        val suggestions = ALL_REQUIRED_FIELDS.filterNot { it == MetadataField.FIT }.map { highConfidenceSuggestion(it) }

        val result = evaluateAutoSaveEligibility(suggestions, shoesId, CATEGORIES)

        assertEquals(AutoSaveEligibility.Eligible, result)
    }

    @Test
    fun `not eligible when Fit is missing for an apparel category where it does apply`() {
        val suggestions = ALL_REQUIRED_FIELDS.filterNot { it == MetadataField.FIT }.map { highConfidenceSuggestion(it) }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertTrue(result is AutoSaveEligibility.NotEligible)
        assertEquals(listOf(MetadataField.FIT), (result as AutoSaveEligibility.NotEligible).reasons)
    }

    @Test
    fun `a single MEDIUM-confidence required field blocks auto-save`() {
        val suggestions =
            ALL_REQUIRED_FIELDS.map {
                if (it == MetadataField.FABRIC) mediumConfidenceSuggestion(it) else highConfidenceSuggestion(it)
            }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertEquals(AutoSaveEligibility.NotEligible(listOf(MetadataField.FABRIC)), result)
    }

    @Test
    fun `a completely missing required field is reported as a reason`() {
        val suggestions =
            ALL_REQUIRED_FIELDS.filterNot { it == MetadataField.OCCASION }.map {
                highConfidenceSuggestion(
                    it,
                )
            }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertEquals(AutoSaveEligibility.NotEligible(listOf(MetadataField.OCCASION)), result)
    }

    @Test
    fun `optional fields like Brand and Warmth never block auto-save even when entirely absent`() {
        val suggestions = ALL_REQUIRED_FIELDS.map { highConfidenceSuggestion(it) }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertEquals(AutoSaveEligibility.Eligible, result)
    }

    @Test
    fun `multiple unmet required fields are all reported as reasons`() {
        val suggestions =
            ALL_REQUIRED_FIELDS
                .filterNot { it == MetadataField.MATERIAL || it == MetadataField.SEASON }
                .map { highConfidenceSuggestion(it) }

        val result = evaluateAutoSaveEligibility(suggestions, topsId, CATEGORIES)

        assertTrue(result is AutoSaveEligibility.NotEligible)
        assertEquals(
            setOf(MetadataField.MATERIAL, MetadataField.SEASON),
            (result as AutoSaveEligibility.NotEligible).reasons.toSet(),
        )
    }
}
