package com.wardrobe.app.feature.capture.review

import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.Category

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100

/**
 * M23 — a real-device debugging aid answering "what did the model actually
 * return for this photo?" from a debug logcat line instead of guessing.
 * Reuses exactly the same [missingFieldReason]/[isSuggestionApplied] logic
 * the review screen itself renders from, so what's logged can never drift
 * out of sync with what the user sees. Pure and Android-free (no `Bitmap`/
 * `Log`/`Context`) so it's directly unit-testable — [GarmentReviewMetadataViewModel]
 * is the only caller, and only logs this in debuggable builds.
 *
 * Never includes the photo itself, an API key, or any user-identifying
 * data — only field names, the AI's own returned attribute values (e.g.
 * "Pink", "Cotton" — exactly what's already shown on the review screen),
 * confidence numbers, and resolved/rejected booleans.
 */
internal fun formatMetadataPipelineDiagnostics(
    source: AiResultSource?,
    suggestions: List<MetadataSuggestion>,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
    categories: List<Category>,
): String {
    val header = "Metadata pipeline diagnostics — source=${source?.name ?: "UNKNOWN"}"
    val fieldLines =
        MetadataField.entries
            .filter(::isBindableField)
            .flatMap { field -> diagnosticLines(field, source, suggestions, form, reference, categories) }
    return (listOf(header) + fieldLines).joinToString("\n")
}

private fun diagnosticLines(
    field: MetadataField,
    source: AiResultSource?,
    suggestions: List<MetadataSuggestion>,
    form: GarmentMetadataFormState,
    reference: ReviewReferenceData,
    categories: List<Category>,
): List<String> {
    val fieldSuggestions = suggestions.filter { it.field == field }
    if (fieldSuggestions.isEmpty()) {
        val reason = missingFieldReason(field, form.categoryId, categories, source)
        return listOf("  $field: NOT_RETURNED reason=$reason")
    }
    return fieldSuggestions.map { suggestion ->
        val applied = isSuggestionApplied(form, field, suggestion.value, reference)
        val confidencePercent =
            suggestion.confidence?.let { "${(it * CONFIDENCE_PERCENT_MULTIPLIER).toInt()}%" } ?: "none"
        "  $field: value=\"${suggestion.value}\" confidence=$confidencePercent " +
            "tier=${suggestion.confidenceTier} resolved=$applied"
    }
}
