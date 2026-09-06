package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import org.junit.Assert.assertEquals
import org.junit.Test

private val topsId = CategoryId(1)
private val shoesId = CategoryId(2)
private val CATEGORIES =
    listOf(
        Category(topsId, "Tops", null, CategoryLevel.TOP),
        Category(shoesId, "Shoes", null, CategoryLevel.TOP),
    )

/**
 * M23 — proves the three "why is this field missing" states are computed
 * correctly and never collapsed: category-inapplicable always wins (a
 * missing Fit on a Shoes item is N/A regardless of provider), then provider
 * support, then genuinely-undetected-this-time.
 */
class MissingFieldReasonTest {
    @Test
    fun `a category-inapplicable field is NOT_APPLICABLE even when the provider would otherwise support it`() {
        assertEquals(
            MissingFieldReason.NOT_APPLICABLE,
            missingFieldReason(MetadataField.FIT, shoesId, CATEGORIES, AiResultSource.CLOUD),
        )
    }

    @Test
    fun `a field the on-device engine has no real signal for is NOT_SUPPORTED, not Unknown`() {
        assertEquals(
            MissingFieldReason.NOT_SUPPORTED,
            missingFieldReason(MetadataField.MATERIAL, topsId, CATEGORIES, AiResultSource.ON_DEVICE),
        )
        assertEquals(
            MissingFieldReason.NOT_SUPPORTED,
            missingFieldReason(MetadataField.FABRIC, topsId, CATEGORIES, AiResultSource.ON_DEVICE),
        )
        assertEquals(
            MissingFieldReason.NOT_SUPPORTED,
            missingFieldReason(MetadataField.CATEGORY, topsId, CATEGORIES, AiResultSource.ON_DEVICE),
        )
    }

    @Test
    fun `a field the on-device engine genuinely supports is NOT_DETECTED when missing, not NOT_SUPPORTED`() {
        assertEquals(
            MissingFieldReason.NOT_DETECTED,
            missingFieldReason(MetadataField.PATTERN, topsId, CATEGORIES, AiResultSource.ON_DEVICE),
        )
        assertEquals(
            MissingFieldReason.NOT_DETECTED,
            missingFieldReason(MetadataField.BRAND, topsId, CATEGORIES, AiResultSource.ON_DEVICE),
        )
    }

    @Test
    fun `cloud never reports NOT_SUPPORTED since every field is requested`() {
        assertEquals(
            MissingFieldReason.NOT_DETECTED,
            missingFieldReason(MetadataField.MATERIAL, topsId, CATEGORIES, AiResultSource.CLOUD),
        )
    }

    @Test
    fun `a null source (nothing to summarize yet) degrades to NOT_DETECTED rather than guessing support`() {
        assertEquals(
            MissingFieldReason.NOT_DETECTED,
            missingFieldReason(MetadataField.MATERIAL, topsId, CATEGORIES, null),
        )
    }
}
