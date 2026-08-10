package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataFieldSupport
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.garment.Category

/** Top-level bucket names ([com.wardrobe.app.core.database.WardrobeDatabase.SeedCallback]'s
 * own seed list) that have a real FIT/NECKLINE/SLEEVE_LENGTH concept —
 * apparel, not accessories/footwear/jewelry. A name-matching heuristic, not
 * a fixed enum (categories are user-editable free text) — same documented,
 * degrade-gracefully-if-renamed pattern as `Occasion.impliedDressCode()`.
 * If a category's top-level ancestor can't be resolved or its name doesn't
 * match this list, applicability is [FieldApplicability.UNKNOWN] (never
 * force-guessed either way). */
private val APPAREL_TOP_LEVEL_CATEGORIES = setOf("Tops", "Bottoms", "Dresses", "Jumpsuits", "Outerwear")

/** Every other [MetadataField] is always applicable — these three are the
 * only ones whose relevance genuinely depends on what kind of item this is
 * (a sock has no neckline, a belt has no sleeve length, a hat has no fit). */
private val CATEGORY_GATED_FIELDS = setOf(MetadataField.FIT, MetadataField.NECKLINE, MetadataField.SLEEVE_LENGTH)

internal enum class FieldApplicability { APPLICABLE, NOT_APPLICABLE, UNKNOWN }

internal fun fieldApplicability(
    field: MetadataField,
    categoryId: CategoryId?,
    categories: List<Category>,
): FieldApplicability {
    val topLevelName = categoryId?.let { topLevelCategoryName(it, categories) }
    return when {
        field !in CATEGORY_GATED_FIELDS -> FieldApplicability.APPLICABLE
        topLevelName == null -> FieldApplicability.UNKNOWN
        topLevelName in APPAREL_TOP_LEVEL_CATEGORIES -> FieldApplicability.APPLICABLE
        else -> FieldApplicability.NOT_APPLICABLE
    }
}

/** M23 — the three reasons a bindable field can end up with no editable
 * value, kept distinct rather than collapsed into one generic "Unknown":
 * [NOT_APPLICABLE] (this item's category has no such attribute, e.g. a shoe
 * has no sleeve length — see [fieldApplicability]), [NOT_SUPPORTED] (the AI
 * provider that actually ran this analysis has no real signal for this
 * field at all — see [MetadataFieldSupport]; on-device today, that's every
 * field except Color/Pattern/Brand), and [NOT_DETECTED] (the provider is
 * genuinely capable of this field but didn't produce a value for this
 * specific photo — the only one of the three that's actually "please
 * choose, AI couldn't tell this time"). */
internal enum class MissingFieldReason { NOT_APPLICABLE, NOT_SUPPORTED, NOT_DETECTED }

/** [source] is `null` when there's nothing to summarize yet (e.g. extraction
 * failed before any metadata call ran) — every field degrades to
 * [MissingFieldReason.NOT_DETECTED] rather than guessing supported/not. */
internal fun missingFieldReason(
    field: MetadataField,
    categoryId: CategoryId?,
    categories: List<Category>,
    source: AiResultSource?,
): MissingFieldReason =
    when {
        fieldApplicability(field, categoryId, categories) == FieldApplicability.NOT_APPLICABLE -> {
            MissingFieldReason.NOT_APPLICABLE
        }

        source != null && !MetadataFieldSupport.isSupported(field, source) -> {
            MissingFieldReason.NOT_SUPPORTED
        }

        else -> {
            MissingFieldReason.NOT_DETECTED
        }
    }

/** Walks up to the root ancestor via a plain loop (rather than recursion or
 * early returns) so this has exactly one `return` — a missing id anywhere
 * in the chain (a dangling/broken reference) simply makes `current` `null`,
 * which the loop condition and the final `?.name` both already handle. */
private fun topLevelCategoryName(
    categoryId: CategoryId,
    categories: List<Category>,
): String? {
    val byId = categories.associateBy { it.id }
    var current: Category? = byId[categoryId]
    while (current?.parentId != null) {
        current = byId[current.parentId]
    }
    return current?.name
}
