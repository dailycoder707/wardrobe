package com.wardrobe.app.core.model.intelligence

import com.wardrobe.app.core.model.common.GarmentId

/**
 * A group of garments that look like possible duplicates — surfaced for the
 * user to review, never auto-deleted (the brief is explicit: "do not delete
 * automatically"). [matchedOnCategory]/[matchedOnColor] are always `true`
 * (that's the grouping key); [matchedOnBrand]/[similarUsage] are additional,
 * non-authoritative signals strengthening (never weakening) the group's
 * confidence.
 */
data class DuplicateGroup(
    val garmentIds: List<GarmentId>,
    val matchedOnCategory: Boolean,
    val matchedOnColor: Boolean,
    val matchedOnBrand: Boolean,
    val similarUsage: Boolean,
)
