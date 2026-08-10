package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

/**
 * M10's confidence-driven autofill (ADR-012 §7): a HIGH-tier suggestion is
 * auto-selected, MEDIUM is offered as a tappable chip, LOW is shown but
 * never auto-applied — see [autoFillForm]/[applySuggestion] (both in
 * `MetadataSuggestionApply.kt`)/[isSuggestionApplied]/[clearSuggestionField].
 * A suggestion whose value doesn't resolve to a real reference-data row or
 * a real enum constant is simply not applied, never guessed into the
 * nearest match (Constitution rule 4). `WARMTH` has no matching
 * single-value field on [com.wardrobe.app.core.model.garment.Garment]
 * today (its `warmthRating` is a 1..5 numeric scale with no defined
 * text-to-number mapping) — that suggestion is still surfaced (confidence
 * and all) in the review screen's suggestion list, just not bound to a
 * form control.
 *
 * Split into this file (the "is this suggestion currently applied" /
 * "is this field bindable at all" half) and `MetadataSuggestionApply.kt`
 * (the "apply a suggestion to the form" half) purely to stay under this
 * project's file-level `TooManyFunctions` threshold.
 */
internal fun isSuggestionApplied(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): Boolean =
    isReferenceBackedFieldApplied(form, field, value, reference) ?: isEnumOrTextFieldApplied(form, field, value)
        ?: false

private fun isReferenceBackedFieldApplied(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
    reference: ReviewReferenceData,
): Boolean? =
    when (field) {
        MetadataField.CATEGORY, MetadataField.SUBCATEGORY -> {
            nameMatches(
                reference.categories.firstOrNull { it.id == form.categoryId }?.name,
                value,
            )
        }

        MetadataField.BRAND -> {
            nameMatches(reference.brands.firstOrNull { it.id == form.brandId }?.name, value)
        }

        MetadataField.PRIMARY_COLOR -> {
            nameMatches(
                reference.colors.firstOrNull { it.id == form.primaryColorId }?.name,
                value,
            )
        }

        MetadataField.SECONDARY_COLOR -> {
            nameMatches(reference.colors.firstOrNull { it.id == form.secondaryColorId }?.name, value)
        }

        MetadataField.MATERIAL -> {
            nameMatches(reference.materials.firstOrNull { it.id == form.materialId }?.name, value)
        }

        MetadataField.FABRIC -> {
            nameMatches(reference.fabrics.firstOrNull { it.id == form.fabricId }?.name, value)
        }

        MetadataField.OCCASION -> {
            reference.occasions
                .firstOrNull { nameMatches(it.name, value) }
                ?.id
                ?.let { it in form.occasionIds }
        }

        MetadataField.STYLE_TAG -> {
            reference.tags
                .firstOrNull { nameMatches(it.name, value) }
                ?.id
                ?.let { it in form.tagIds }
        }

        else -> {
            null
        }
    }

private fun isEnumOrTextFieldApplied(
    form: GarmentMetadataFormState,
    field: MetadataField,
    value: String,
): Boolean? =
    when (field) {
        MetadataField.PATTERN -> nameMatches(form.patternText, value)
        MetadataField.FIT -> nameMatches(form.fit?.name, value)
        MetadataField.SLEEVE_LENGTH -> nameMatches(form.sleeveLength?.name, value)
        MetadataField.LENGTH -> nameMatches(form.length?.name, value)
        MetadataField.NECKLINE -> nameMatches(form.neckline?.name, value)
        MetadataField.GENDER -> nameMatches(form.gender?.name, value)
        MetadataField.WATERPROOF_LEVEL -> nameMatches(form.waterproofLevel?.name, value)
        MetadataField.SEASON -> enumValueOrNull<Season>(value)?.let { it in form.seasons }
        MetadataField.DRESS_CODE -> enumValueOrNull<DressCode>(value)?.let { it in form.dressCodes }
        else -> null
    }

/** Whether [field] is bound to a real form control at all — the fields
 * called out in this file's own KDoc (`WARMTH`) return `false`, so the
 * review screen can render that suggestion read-only rather than as a
 * dead tap target. */
internal fun isBindableField(field: MetadataField): Boolean =
    field in
        setOf(
            MetadataField.CATEGORY,
            MetadataField.SUBCATEGORY,
            MetadataField.BRAND,
            MetadataField.PRIMARY_COLOR,
            MetadataField.SECONDARY_COLOR,
            MetadataField.MATERIAL,
            MetadataField.FABRIC,
            MetadataField.PATTERN,
            MetadataField.FIT,
            MetadataField.SLEEVE_LENGTH,
            MetadataField.LENGTH,
            MetadataField.NECKLINE,
            MetadataField.GENDER,
            MetadataField.WATERPROOF_LEVEL,
            MetadataField.SEASON,
            MetadataField.DRESS_CODE,
            MetadataField.OCCASION,
            MetadataField.STYLE_TAG,
        )

/** M24 Phase 3 — normalizes whitespace/hyphenation/case before comparing,
 * never a semantic/fuzzy guess: a cloud model's free-text output legitimately
 * varies on formatting alone (`"T-Shirt"` vs `"T Shirt"` vs `"TShirt"`,
 * extra spaces, a trailing newline) for what is genuinely the same
 * reference-data name. Two actually-different words (`"Navy"` vs
 * `"Navy Blue"`) still never match — this only collapses formatting noise,
 * it does not invent an alias table. */
internal fun nameMatches(
    candidate: String?,
    value: String,
): Boolean = candidate != null && normalizeForMatch(candidate) == normalizeForMatch(value)

/** Whitespace and hyphens carry no semantic content in a garment attribute
 * name — stripped entirely (not collapsed to a single space) so
 * `"T-Shirt"`, `"T Shirt"`, and `"TShirt"` all normalize identically,
 * whichever formatting a cloud model's free text happens to use. */
private val FORMATTING_NOISE = Regex("[\\s-]+")

private fun normalizeForMatch(text: String): String = text.lowercase().replace(FORMATTING_NOISE, "")

internal inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    runCatching {
        enumValueOf<T>(
            value
                .trim()
                .uppercase()
                .replace(' ', '_')
                .replace('-', '_'),
        )
    }.getOrNull()
