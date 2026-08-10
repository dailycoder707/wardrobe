package com.wardrobe.app.core.model.garment

/**
 * A fixed, non-user-editable vocabulary, same shape as [Fit]/[SleeveLength].
 * `NONE` is a genuine, positive claim ("this item has no water resistance"),
 * distinct from the column simply being `null` ("nobody has determined
 * this yet") — the same "unknown vs. a real negative answer" distinction
 * `Garment.warmthRating`/`breathabilityRating` being nullable already
 * relies on for other ratings.
 */
enum class WaterproofLevel { NONE, WATER_RESISTANT, WATERPROOF }
