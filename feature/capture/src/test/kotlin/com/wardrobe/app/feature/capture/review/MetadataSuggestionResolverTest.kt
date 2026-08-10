package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fabric
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.model.outfit.Occasion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val topsId = CategoryId(1)
private val tShirtId = CategoryId(2)
private val cottonId = MaterialId(1)
private val jerseyId = FabricId(1)
private val navyId = ColorId(1)
private val charcoalId = ColorId(2)
private val casualId = OccasionId(1)
private val nikeId = BrandId(1)
private val vintageTagId = TagId(1)

private val REFERENCE =
    ReviewReferenceData(
        categories =
            listOf(
                Category(topsId, "Tops", null, CategoryLevel.TOP),
                Category(tShirtId, "T-Shirts", topsId, CategoryLevel.SUB),
            ),
        brands = listOf(Brand(nikeId, "Nike", null)),
        colors = listOf(Color(navyId, "Navy", "#000080"), Color(charcoalId, "Charcoal", "#36454F")),
        materials = listOf(Material(cottonId, "Cotton")),
        fabrics = listOf(Fabric(jerseyId, "Jersey")),
        occasions = listOf(Occasion(casualId, "Casual")),
        tags = listOf(Tag(vintageTagId, "Vintage")),
    )

private fun suggestion(
    field: MetadataField,
    value: String,
    confidence: Float? = 0.9f,
) = MetadataSuggestion(
    field = field,
    value = value,
    confidence = confidence,
    provenance = AiResultProvenance(AiResultSource.CLOUD, "openai", "gpt-vision", "metadata-v2", Instant.EPOCH),
)

class MetadataSuggestionResolverTest {
    // --- isBindableField ---

    @Test
    fun `every newly-bindable field reports itself as bindable`() {
        val newlyBindable =
            listOf(
                MetadataField.SUBCATEGORY,
                MetadataField.SECONDARY_COLOR,
                MetadataField.FABRIC,
                MetadataField.NECKLINE,
                MetadataField.GENDER,
                MetadataField.WATERPROOF_LEVEL,
                MetadataField.OCCASION,
                MetadataField.STYLE_TAG,
            )
        newlyBindable.forEach { field -> assertTrue("$field should be bindable", isBindableField(field)) }
    }

    @Test
    fun `WARMTH has no bound form control and is not bindable`() {
        assertFalse(isBindableField(MetadataField.WARMTH))
    }

    // --- autoFillForm: subcategory priority ---

    @Test
    fun `a HIGH-confidence SUBCATEGORY overrides a HIGH-confidence CATEGORY regardless of list order`() {
        val categoryFirst =
            autoFillForm(
                listOf(suggestion(MetadataField.CATEGORY, "Tops"), suggestion(MetadataField.SUBCATEGORY, "T-Shirts")),
                REFERENCE,
            )
        val subcategoryFirst =
            autoFillForm(
                listOf(suggestion(MetadataField.SUBCATEGORY, "T-Shirts"), suggestion(MetadataField.CATEGORY, "Tops")),
                REFERENCE,
            )

        assertEquals(tShirtId, categoryFirst.categoryId)
        assertEquals(tShirtId, subcategoryFirst.categoryId)
    }

    @Test
    fun `CATEGORY alone resolves to the top-level category`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.CATEGORY, "Tops")), REFERENCE)

        assertEquals(topsId, form.categoryId)
    }

    // --- autoFillForm / applySuggestion: newly-bindable fields ---

    @Test
    fun `autoFillForm binds SECONDARY_COLOR, FABRIC, NECKLINE, GENDER and WATERPROOF_LEVEL`() {
        val form =
            autoFillForm(
                listOf(
                    suggestion(MetadataField.SECONDARY_COLOR, "Charcoal"),
                    suggestion(MetadataField.FABRIC, "Jersey"),
                    suggestion(MetadataField.NECKLINE, "Crew"),
                    suggestion(MetadataField.GENDER, "Unisex"),
                    suggestion(MetadataField.WATERPROOF_LEVEL, "Water Resistant"),
                ),
                REFERENCE,
            )

        assertEquals(charcoalId, form.secondaryColorId)
        assertEquals(jerseyId, form.fabricId)
        assertEquals(Neckline.CREW, form.neckline)
        assertEquals(GarmentGender.UNISEX, form.gender)
        assertEquals(WaterproofLevel.WATER_RESISTANT, form.waterproofLevel)
    }

    @Test
    fun `autoFillForm binds OCCASION and STYLE_TAG into their multi-valued sets`() {
        val form =
            autoFillForm(
                listOf(suggestion(MetadataField.OCCASION, "Casual"), suggestion(MetadataField.STYLE_TAG, "Vintage")),
                REFERENCE,
            )

        assertEquals(setOf(casualId), form.occasionIds)
        assertEquals(setOf(vintageTagId), form.tagIds)
    }

    /** M24's actual acceptance criterion, proven as a repeatable automated
     * test rather than only a manual real-device check: a realistic
     * multi-field cloud response for a clear garment photo (a navy cotton
     * jersey T-shirt) — the kind `MetadataPromptSupport`'s real parser
     * would hand to `autoFillForm` — resolves and auto-populates every
     * field simultaneously, not just one at a time. Brand is deliberately
     * omitted (no visible brand evidence on this garment), proving the
     * absence of a field is never filled with a guess. */
    @Test
    fun `a realistic full cloud response for a clear garment auto-populates every resolvable field at once`() {
        val cloudResponse =
            listOf(
                suggestion(MetadataField.CATEGORY, "Tops"),
                suggestion(MetadataField.SUBCATEGORY, "T-Shirts"),
                suggestion(MetadataField.PRIMARY_COLOR, "Navy"),
                suggestion(MetadataField.PATTERN, "Solid"),
                suggestion(MetadataField.MATERIAL, "Cotton"),
                suggestion(MetadataField.FABRIC, "Jersey"),
                suggestion(MetadataField.FIT, "Regular"),
                suggestion(MetadataField.DRESS_CODE, "Casual"),
                suggestion(MetadataField.SEASON, "Summer"),
                suggestion(MetadataField.OCCASION, "Casual"),
            )

        val form = autoFillForm(cloudResponse, REFERENCE)

        assertEquals(tShirtId, form.categoryId) // SUBCATEGORY overrides the coarser CATEGORY pick
        assertEquals(navyId, form.primaryColorId)
        assertEquals("Solid", form.patternText)
        assertEquals(cottonId, form.materialId)
        assertEquals(jerseyId, form.fabricId)
        assertEquals(Fit.REGULAR, form.fit)
        assertEquals(setOf(DressCode.CASUAL), form.dressCodes)
        assertEquals(setOf(Season.SUMMER), form.seasons)
        assertEquals(setOf(casualId), form.occasionIds)
        // No brand suggestion was ever sent — never fabricated as a guess.
        assertEquals(null, form.brandId)
    }

    @Test
    fun `a suggestion whose value doesn't match any existing reference row is never applied`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.FABRIC, "Neoprene")), REFERENCE)

        assertEquals(null, form.fabricId)
    }

    // --- M24 Phase 3: deterministic normalization, never a semantic guess ---

    @Test
    fun `case differences from a cloud model's free text still resolve to the real reference row`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.FABRIC, "JERSEY")), REFERENCE)

        assertEquals(jerseyId, form.fabricId)
    }

    @Test
    fun `hyphen-vs-space formatting differences still resolve to the same reference row`() {
        val hyphenated = autoFillForm(listOf(suggestion(MetadataField.BRAND, "Ni-ke")), REFERENCE)
        val extraWhitespace = autoFillForm(listOf(suggestion(MetadataField.BRAND, "  Nike  ")), REFERENCE)

        assertEquals(nikeId, hyphenated.brandId)
        assertEquals(nikeId, extraWhitespace.brandId)
    }

    @Test
    fun `an actually different value never fuzzy-matches a similarly-named reference row`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.PRIMARY_COLOR, "Navy Blue")), REFERENCE)

        // "Navy Blue" is genuinely not "Navy" — normalization collapses
        // formatting noise, it must never guess two different words together.
        assertEquals(null, form.primaryColorId)
    }

    @Test
    fun `only HIGH-confidence suggestions are auto-filled, MEDIUM is left for manual review`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.FABRIC, "Jersey", confidence = 0.6f)), REFERENCE)

        assertEquals(null, form.fabricId)
    }

    /** M24 Phase 4 — a cloud response with mixed per-field confidence (as a
     * real vision model genuinely produces: certain about color, unsure
     * about material) must apply each field on its own evidence, never as
     * if one summary/average confidence governed every field. If gating
     * were average-based, a HIGH Category alongside a LOW Brand could
     * either wrongly block Category or wrongly admit Brand — neither
     * happens here. */
    @Test
    fun `each field in a mixed-confidence cloud response is gated on its own confidence, never an average`() {
        val form =
            autoFillForm(
                listOf(
                    suggestion(MetadataField.CATEGORY, "Tops", confidence = 0.95f),
                    suggestion(MetadataField.MATERIAL, "Cotton", confidence = 0.6f),
                    suggestion(MetadataField.BRAND, "Nike", confidence = null),
                ),
                REFERENCE,
            )

        assertEquals(topsId, form.categoryId)
        assertEquals(null, form.materialId)
        assertEquals(null, form.brandId)
    }

    // --- isSuggestionApplied ---

    @Test
    fun `isSuggestionApplied reports true once a reference-backed suggestion is bound to the form`() {
        val form = GarmentMetadataFormState(fabricId = jerseyId)

        assertTrue(isSuggestionApplied(form, MetadataField.FABRIC, "Jersey", REFERENCE))
    }

    @Test
    fun `isSuggestionApplied reports true for an applied multi-valued OCCASION`() {
        val form = GarmentMetadataFormState(occasionIds = setOf(casualId))

        assertTrue(isSuggestionApplied(form, MetadataField.OCCASION, "Casual", REFERENCE))
    }

    @Test
    fun `isSuggestionApplied reports false when nothing has been bound yet`() {
        val form = GarmentMetadataFormState()

        assertFalse(isSuggestionApplied(form, MetadataField.FABRIC, "Jersey", REFERENCE))
    }

    @Test
    fun `isSuggestionApplied reports true for an applied enum-backed NECKLINE`() {
        val form = GarmentMetadataFormState(neckline = Neckline.V_NECK)

        assertTrue(isSuggestionApplied(form, MetadataField.NECKLINE, "V_Neck", REFERENCE))
    }

    // --- clearSuggestionField ---

    @Test
    fun `clearSuggestionField removes only the matching OCCASION id, leaving other occasions intact`() {
        val otherOccasionId = OccasionId(2)
        val form = GarmentMetadataFormState(occasionIds = setOf(casualId, otherOccasionId))
        val referenceWithTwoOccasions =
            REFERENCE.copy(occasions = REFERENCE.occasions + Occasion(otherOccasionId, "Formal"))

        val cleared = clearSuggestionField(form, MetadataField.OCCASION, "Casual", referenceWithTwoOccasions)

        assertEquals(setOf(otherOccasionId), cleared.occasionIds)
    }

    @Test
    fun `clearSuggestionField nulls out a single-valued enum field`() {
        val form = GarmentMetadataFormState(waterproofLevel = WaterproofLevel.WATERPROOF)

        val cleared = clearSuggestionField(form, MetadataField.WATERPROOF_LEVEL, "Waterproof", REFERENCE)

        assertEquals(null, cleared.waterproofLevel)
    }

    // --- FieldApplicability-gated required field: FIT ---

    @Test
    fun `autoFillForm still binds FIT the same way as any other single-value enum field`() {
        val form = autoFillForm(listOf(suggestion(MetadataField.FIT, "Slim")), REFERENCE)

        assertEquals(Fit.SLIM, form.fit)
    }
}
