package com.wardrobe.app.core.ai.prompt

/**
 * Named, explicit prompt versions (ADR-012 §5) — passed into every
 * [com.wardrobe.app.core.ai.gateway.AiGateway] call and embedded in the
 * cache key and provenance record. Bumping one naturally invalidates the
 * cache for that version and makes "regenerate with the improved prompt" a
 * real, explicit action later, rather than an ambiguous guess about which
 * prompt produced a given cached result.
 */
object PromptVersions {
    const val METADATA_V1 = "metadata-v1"

    /** AI Wardrobe Assistant Parts 1-3: the metadata prompt now teaches the
     * model FABRIC/NECKLINE/GENDER/WATERPROOF_LEVEL/OCCASION vocabulary and
     * explicit not-applicable guidance — a real prompt content change, so
     * this is a genuinely new version (invalidates `METADATA_V1`-keyed
     * cache rows via the existing cache-key/prompt-version design, not a
     * relabeling of the same prompt). */
    const val METADATA_V2 = "metadata-v2"
    const val STYLING_V1 = "styling-v1"
    const val EXTRACTION_V1 = "extraction-v1"
    const val RECONSTRUCTION_V1 = "reconstruction-v1"
    const val TRYON_V1 = "tryon-v1"
}
