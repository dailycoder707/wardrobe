package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.ConfidenceTier
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category

/**
 * AI Wardrobe Assistant Part 2 — the production auto-save rule: a garment
 * auto-saves only once every *required* field is either genuinely
 * HIGH-confidence or [FieldApplicability.NOT_APPLICABLE] for this item's
 * category; a single MEDIUM/LOW/missing required field routes to the
 * ordinary manual review screen instead. This checks the *suggestions*
 * directly (not just whether the form happens to be populated) — a field a
 * user manually tapped from a MEDIUM/LOW chip must not count as
 * AI-confident for this gate's purposes.
 *
 * Deliberately excludes `WARMTH`/`BRAND`/`SECONDARY_COLOR`/`SLEEVE_LENGTH`/
 * `NECKLINE`/`GENDER`/`WATERPROOF_LEVEL`/`STYLE_TAG` — the optional-field
 * list — since blocking auto-save on, say, a plain white tee's undetectable
 * brand would defeat the entire point of this feature.
 */
private val REQUIRED_FIELDS =
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

internal sealed interface AutoSaveEligibility {
    data object Eligible : AutoSaveEligibility

    data class NotEligible(
        val reasons: List<MetadataField>,
    ) : AutoSaveEligibility
}

internal fun evaluateAutoSaveEligibility(
    suggestions: List<MetadataSuggestion>,
    categoryId: CategoryId?,
    categories: List<Category>,
): AutoSaveEligibility {
    val unmet =
        REQUIRED_FIELDS.filterNot { field -> isRequiredFieldSatisfied(field, suggestions, categoryId, categories) }
    return if (unmet.isEmpty()) AutoSaveEligibility.Eligible else AutoSaveEligibility.NotEligible(unmet)
}

private fun isRequiredFieldSatisfied(
    field: MetadataField,
    suggestions: List<MetadataSuggestion>,
    categoryId: CategoryId?,
    categories: List<Category>,
): Boolean {
    if (fieldApplicability(field, categoryId, categories) == FieldApplicability.NOT_APPLICABLE) return true
    return suggestions.any { it.field == field && it.confidenceTier == ConfidenceTier.HIGH }
}
