package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.ConfidenceTier
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
import com.wardrobe.app.core.model.garment.WaterproofLevel

/** The "apply a suggestion to the form" half of
 * [MetadataSuggestionResolver.kt] — split into its own file purely to stay
 * under this project's file-level `TooManyFunctions` threshold (same
 * reasoning as `MetadataSuggestionClear.kt`), and each `when` is further
 * split into a single-value and a multi-value half to stay under the
 * `CyclomaticComplexMethod` threshold.
 *
 * `CATEGORY`/`SUBCATEGORY` both resolve into the same
 * [GarmentMetadataFormState.categoryId] slot — [autoFillForm] processes
 * `CATEGORY` first and `SUBCATEGORY` second regardless of the AI response's
 * own ordering (see [fieldApplicationPriority]), so a confidently-identified
 * specific subcategory always wins over a more general top-level category
 * rather than depending on which one happened to appear first in the list.
 */
internal fun autoFillForm(
    suggestions: List<MetadataSuggestion>,
    reference: ReviewReferenceData,
): GarmentMetadataFormState =
    suggestions
        .filter { it.confidenceTier == ConfidenceTier.HIGH }
        .sortedBy { fieldApplicationPriority(it.field) }
        .fold(GarmentMetadataFormState()) { form, suggestion ->
            applySuggestion(form, suggestion.field, suggestion.value, reference) ?: form
        }

/** `SUBCATEGORY` must always be folded after `CATEGORY` so it can override
 * the coarser category pick — every other field is order-independent
 * (priority 0). A stable sort preserves original relative order among
 * equal-priority fields. */
private fun fieldApplicationPriority(field: MetadataField): Int = if (field == MetadataField.SUBCATEGORY) 1 else 0

internal fun applySuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState? =
    applyCategoryOrBrandSuggestion(form, field, value, reference)
        ?: applyColorOrFabricSuggestion(form, field, value, reference)
        ?: applyMultiValueReferenceSuggestion(form, field, value, reference)
        ?: applySingleValueEnumSuggestion(form, field, value)
        ?: applyMultiValueEnumSuggestion(form, field, value)

private fun applyCategoryOrBrandSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.CATEGORY -> {
            reference.categories.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(categoryId = it.id) }
        }

        MetadataField.SUBCATEGORY -> {
            reference.categories
                .firstOrNull { it.level == CategoryLevel.SUB && nameMatches(it.name, value) }
                ?.let { form.copy(categoryId = it.id) }
        }

        MetadataField.BRAND -> {
            reference.brands.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(brandId = it.id) }
        }

        else -> {
            null
        }
    }

private fun applyColorOrFabricSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.PRIMARY_COLOR -> {
            reference.colors.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(primaryColorId = it.id) }
        }

        MetadataField.SECONDARY_COLOR -> {
            reference.colors.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(secondaryColorId = it.id) }
        }

        MetadataField.MATERIAL -> {
            reference.materials.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(materialId = it.id) }
        }

        MetadataField.FABRIC -> {
            reference.fabrics.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(fabricId = it.id) }
        }

        else -> {
            null
        }
    }

private fun applyMultiValueReferenceSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.OCCASION -> {
            reference.occasions
                .firstOrNull { nameMatches(it.name, value) }
                ?.let { form.copy(occasionIds = form.occasionIds + it.id) }
        }

        MetadataField.STYLE_TAG -> {
            reference.tags.firstOrNull { nameMatches(it.name, value) }?.let { form.copy(tagIds = form.tagIds + it.id) }
        }

        else -> {
            null
        }
    }

private fun applySingleValueEnumSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): GarmentMetadataFormState? =
    applyGarmentShapeSuggestion(form, field, value) ?: applyGarmentIdentitySuggestion(form, field, value)

private fun applyGarmentShapeSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.PATTERN -> {
            form.copy(patternText = value)
        }

        MetadataField.FIT -> {
            enumValueOrNull<Fit>(value)?.let { form.copy(fit = it) }
        }

        MetadataField.SLEEVE_LENGTH -> {
            enumValueOrNull<SleeveLength>(value)?.let { form.copy(sleeveLength = it) }
        }

        MetadataField.LENGTH -> {
            enumValueOrNull<GarmentLength>(value)?.let { form.copy(length = it) }
        }

        else -> {
            null
        }
    }

private fun applyGarmentIdentitySuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.NECKLINE -> {
            enumValueOrNull<Neckline>(value)?.let { form.copy(neckline = it) }
        }

        MetadataField.GENDER -> {
            enumValueOrNull<GarmentGender>(value)?.let { form.copy(gender = it) }
        }

        MetadataField.WATERPROOF_LEVEL -> {
            enumValueOrNull<WaterproofLevel>(value)?.let { form.copy(waterproofLevel = it) }
        }

        else -> {
            null
        }
    }

private fun applyMultiValueEnumSuggestion(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.SEASON -> {
            enumValueOrNull<Season>(value)?.let { form.copy(seasons = form.seasons + it) }
        }

        MetadataField.DRESS_CODE -> {
            enumValueOrNull<DressCode>(
                value,
            )?.let { form.copy(dressCodes = form.dressCodes + it) }
        }

        else -> {
            null
        }
    }
