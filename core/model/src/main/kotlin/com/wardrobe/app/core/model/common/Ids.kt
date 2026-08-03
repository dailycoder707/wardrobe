package com.wardrobe.app.core.model.common

/**
 * One value class per entity so a `GarmentId` can never be passed where an
 * `OutfitId` is expected, at zero runtime cost. Room entities themselves use
 * plain `Long` primary keys (autoGenerate) — these wrap the id only at the
 * domain-model boundary; the entity↔domain mapping (Phase 5a, core:data) does
 * the unwrapping/wrapping.
 */
@JvmInline
value class GarmentId(
    val value: Long,
)

@JvmInline
value class CategoryId(
    val value: Long,
)

@JvmInline
value class ColorId(
    val value: Long,
)

@JvmInline
value class MaterialId(
    val value: Long,
)

@JvmInline
value class BrandId(
    val value: Long,
)

@JvmInline
value class TagId(
    val value: Long,
)

@JvmInline
value class OccasionId(
    val value: Long,
)

@JvmInline
value class OutfitId(
    val value: Long,
)

@JvmInline
value class WearEventId(
    val value: Long,
)

@JvmInline
value class StyleRuleId(
    val value: Long,
)

@JvmInline
value class FeedbackId(
    val value: Long,
)

@JvmInline
value class TripId(
    val value: Long,
)

@JvmInline
value class PackingListItemId(
    val value: Long,
)

@JvmInline
value class WishlistItemId(
    val value: Long,
)

@JvmInline
value class WeatherCacheId(
    val value: Long,
)

@JvmInline
value class ImageMetadataId(
    val value: Long,
)

@JvmInline
value class BodyProfileId(
    val value: Long,
)

@JvmInline
value class GarmentPlacementTemplateId(
    val value: Long,
)

@JvmInline
value class GarmentMaskId(
    val value: Long,
)
