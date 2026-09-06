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

    /** M25 real-device finding: the prompt now spells out FIT/LENGTH/
     * SLEEVE_LENGTH/SEASON/DRESS_CODE enum vocabulary (previously only
     * NECKLINE/GENDER/WATERPROOF_LEVEL were covered) and injects this
     * wardrobe's actual CATEGORY/COLOR/MATERIAL/FABRIC/OCCASION/STYLE_TAG
     * reference-data option lists — a real prompt content change. */
    const val METADATA_V3 = "metadata-v3"
    const val STYLING_V1 = "styling-v1"
    const val EXTRACTION_V1 = "extraction-v1"

    /** M25 real-device follow-up: Gemini's `generateContent` structured JSON
     * output can include a real per-pixel segmentation mask (`box_2d` + a
     * base64 PNG probability map), verified against Google's own
     * documentation — a genuinely different wire shape from [EXTRACTION_V1],
     * which is [com.wardrobe.app.core.ai.gateway.ImageTaskAdapter]'s
     * image-in/image-out multipart contract. Its own prompt version keeps
     * the two cache namespaces from ever colliding for the same image. */
    const val EXTRACTION_GEMINI_SEGMENTATION_V1 = "extraction-gemini-segmentation-v1"
    const val RECONSTRUCTION_V1 = "reconstruction-v1"
    const val TRYON_V1 = "tryon-v1"
}
