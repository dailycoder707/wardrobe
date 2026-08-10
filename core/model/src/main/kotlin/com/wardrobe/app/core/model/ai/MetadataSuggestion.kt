package com.wardrobe.app.core.model.ai

/**
 * One suggested value for one [MetadataField], always editable, never
 * written to the database directly (Constitution rule 4 / rule 7 — "an
 * editable suggestion with a confidence signal, never a fact"; ADR-012 §7's
 * human-review-pipeline invariant). [confidence] is the real, provider-
 * reported scalar this suggestion's [ConfidenceTier] is derived from — a
 * provider that doesn't return one leaves [confidence] `null` and
 * [confidenceTier] follows suit (`null`, not a defaulted `LOW`).
 */
data class MetadataSuggestion(
    val field: MetadataField,
    val value: String,
    val confidence: Float?,
    val provenance: AiResultProvenance,
) {
    val confidenceTier: ConfidenceTier? get() = ConfidenceTier.forConfidence(confidence)
}
