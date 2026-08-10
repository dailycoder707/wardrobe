package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import org.junit.Assert.assertEquals
import org.junit.Test

private val topsId = CategoryId(1)
private val tShirtId = CategoryId(2)
private val shoesId = CategoryId(3)
private val sneakersId = CategoryId(4)
private val renamedTopId = CategoryId(5)
private val orphanSubId = CategoryId(6)

private val CATEGORIES =
    listOf(
        Category(topsId, "Tops", null, CategoryLevel.TOP),
        Category(tShirtId, "T-Shirts", topsId, CategoryLevel.SUB),
        Category(shoesId, "Shoes", null, CategoryLevel.TOP),
        Category(sneakersId, "Sneakers", shoesId, CategoryLevel.SUB),
        Category(renamedTopId, "Footwear (renamed)", null, CategoryLevel.TOP),
        Category(orphanSubId, "Orphaned Sub", CategoryId(999), CategoryLevel.SUB),
    )

class FieldApplicabilityTest {
    @Test
    fun `Fit Neckline and Sleeve Length are applicable for a Tops-descended category`() {
        assertEquals(FieldApplicability.APPLICABLE, fieldApplicability(MetadataField.FIT, tShirtId, CATEGORIES))
        assertEquals(FieldApplicability.APPLICABLE, fieldApplicability(MetadataField.NECKLINE, tShirtId, CATEGORIES))
        assertEquals(
            FieldApplicability.APPLICABLE,
            fieldApplicability(MetadataField.SLEEVE_LENGTH, tShirtId, CATEGORIES),
        )
    }

    @Test
    fun `Fit Neckline and Sleeve Length are applicable for the Tops top-level category itself`() {
        assertEquals(FieldApplicability.APPLICABLE, fieldApplicability(MetadataField.FIT, topsId, CATEGORIES))
    }

    @Test
    fun `Fit Neckline and Sleeve Length are not applicable for a Shoes-descended category`() {
        assertEquals(
            FieldApplicability.NOT_APPLICABLE,
            fieldApplicability(MetadataField.FIT, sneakersId, CATEGORIES),
        )
        assertEquals(
            FieldApplicability.NOT_APPLICABLE,
            fieldApplicability(MetadataField.NECKLINE, sneakersId, CATEGORIES),
        )
        assertEquals(
            FieldApplicability.NOT_APPLICABLE,
            fieldApplicability(MetadataField.SLEEVE_LENGTH, sneakersId, CATEGORIES),
        )
    }

    @Test
    fun `a top-level category renamed away from the seeded apparel bucket names is not applicable, not guessed`() {
        assertEquals(
            FieldApplicability.NOT_APPLICABLE,
            fieldApplicability(MetadataField.FIT, renamedTopId, CATEGORIES),
        )
    }

    @Test
    fun `an orphaned sub-category with a dangling parent id degrades to unknown rather than guessing`() {
        assertEquals(
            FieldApplicability.UNKNOWN,
            fieldApplicability(MetadataField.FIT, orphanSubId, CATEGORIES),
        )
    }

    @Test
    fun `a category id that isn't in the reference list at all degrades to unknown`() {
        assertEquals(
            FieldApplicability.UNKNOWN,
            fieldApplicability(MetadataField.FIT, CategoryId(12345), CATEGORIES),
        )
    }

    @Test
    fun `a null category id is unknown for a category-gated field`() {
        assertEquals(FieldApplicability.UNKNOWN, fieldApplicability(MetadataField.FIT, null, CATEGORIES))
    }

    @Test
    fun `a field that isn't category-gated is always applicable, even with no category chosen`() {
        assertEquals(FieldApplicability.APPLICABLE, fieldApplicability(MetadataField.MATERIAL, null, CATEGORIES))
        assertEquals(
            FieldApplicability.APPLICABLE,
            fieldApplicability(MetadataField.MATERIAL, sneakersId, CATEGORIES),
        )
    }
}
