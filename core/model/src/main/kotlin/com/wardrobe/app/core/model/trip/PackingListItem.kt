package com.wardrobe.app.core.model.trip

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId

/**
 * A derived, explainable line in a trip's packing list (phase-1-architecture.md
 * Section 12). Exactly one of [garmentId] (suggesting an owned item) or
 * [freeTextName] (a gap the closet doesn't cover) is populated — same
 * exactly-one-of-two pattern as [com.wardrobe.app.core.model.wear.WearEvent],
 * enforced the same way (app layer, not schema).
 */
data class PackingListItem(
    val id: PackingListItemId,
    val tripId: TripId,
    val garmentId: GarmentId?,
    val freeTextName: String?,
    val category: String?,
    val isPacked: Boolean,
    val rationale: String?,
) {
    init {
        require((garmentId == null) != (freeTextName == null)) {
            "exactly one of garmentId/freeTextName must be set"
        }
    }
}
