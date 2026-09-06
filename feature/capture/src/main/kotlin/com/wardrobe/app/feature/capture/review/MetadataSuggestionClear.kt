package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

/** The "un-tap a suggestion chip" half of [MetadataSuggestionResolver.kt] —
 * split into its own file purely to stay under this project's file-level
 * `TooManyFunctions` threshold. [reference] is only needed to resolve which
 * specific id to remove from a multi-valued reference-backed field
 * (`OCCASION`/`STYLE_TAG`) — single-valued fields just null out. */
internal fun clearSuggestionField(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState =
    clearReferenceBackedField(form, field, value, reference) ?: clearEnumOrTextField(form, field, value) ?: form

private fun clearReferenceBackedField(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.CATEGORY, MetadataField.SUBCATEGORY -> {
            form.copy(categoryId = null)
        }

        MetadataField.BRAND -> {
            form.copy(brandId = null)
        }

        MetadataField.PRIMARY_COLOR -> {
            form.copy(primaryColorId = null)
        }

        MetadataField.SECONDARY_COLOR -> {
            form.copy(secondaryColorId = null)
        }

        MetadataField.MATERIAL -> {
            form.copy(materialId = null)
        }

        MetadataField.FABRIC -> {
            form.copy(fabricId = null)
        }

        MetadataField.OCCASION -> {
            reference.occasions
                .firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?.let { form.copy(occasionIds = form.occasionIds - it.id) }
        }

        MetadataField.STYLE_TAG -> {
            reference.tags
                .firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?.let { form.copy(tagIds = form.tagIds - it.id) }
        }

        else -> {
            null
        }
    }

private fun clearEnumOrTextField(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): GarmentMetadataFormState? =
    when (field) {
        MetadataField.PATTERN -> {
            form.copy(patternText = "")
        }

        MetadataField.FIT -> {
            form.copy(fit = null)
        }

        MetadataField.SLEEVE_LENGTH -> {
            form.copy(sleeveLength = null)
        }

        MetadataField.LENGTH -> {
            form.copy(length = null)
        }

        MetadataField.NECKLINE -> {
            form.copy(neckline = null)
        }

        MetadataField.GENDER -> {
            form.copy(gender = null)
        }

        MetadataField.WATERPROOF_LEVEL -> {
            form.copy(waterproofLevel = null)
        }

        MetadataField.SEASON -> {
            enumValueOrNull<Season>(value)?.let { form.copy(seasons = form.seasons - it) }
        }

        MetadataField.DRESS_CODE -> {
            enumValueOrNull<DressCode>(
                value,
            )?.let { form.copy(dressCodes = form.dressCodes - it) }
        }

        else -> {
            null
        }
    }
