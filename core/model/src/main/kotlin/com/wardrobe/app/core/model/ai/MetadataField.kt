package com.wardrobe.app.core.model.ai

/**
 * Every garment attribute `GarmentMetadataEngine` (`core:image`) can suggest
 * a value for. Several are naturally multi-valued (a garment can have more
 * than one [SEASON] or [STYLE_TAG]) — that's expressed as multiple
 * [MetadataSuggestion]s sharing the same field, not a list-typed value,
 * keeping the suggestion shape uniform. Lives in `core:model`, not
 * `core:image`, so `StagedImage` (also `core:model`) can carry a
 * `List<MetadataSuggestion>` without `core:model` depending on `core:image`
 * — the same layering reason [AiProviderConfig] lives here too.
 */
enum class MetadataField {
    CATEGORY,
    SUBCATEGORY,
    PRIMARY_COLOR,
    SECONDARY_COLOR,
    PATTERN,
    MATERIAL,

    /** Weave/construction (Denim, Jersey, Twill...) — deliberately distinct
     * from [MATERIAL] (fiber content), see
     * [com.wardrobe.app.core.model.garment.Fabric]'s KDoc. Added for the
     * near-automatic Add-to-Wardrobe pass (AI Wardrobe Assistant Parts 1-3). */
    FABRIC,
    BRAND,
    SLEEVE_LENGTH,
    NECKLINE,
    LENGTH,
    FIT,
    SEASON,
    DRESS_CODE,
    OCCASION,
    GENDER,
    WARMTH,

    /** Added alongside [FABRIC] — a fixed [com.wardrobe.app.core.model.garment.WaterproofLevel],
     * not a continuous rating like [WARMTH]. */
    WATERPROOF_LEVEL,
    STYLE_TAG,
}
