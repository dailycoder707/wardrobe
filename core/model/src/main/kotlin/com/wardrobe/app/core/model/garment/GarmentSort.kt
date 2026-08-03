package com.wardrobe.app.core.model.garment

enum class GarmentSortField {
    RECENTLY_ADDED,
    RECENTLY_WORN,
    ALPHABETICAL,
    BRAND,
    COLOR,
    PRICE,
    WEAR_COUNT,
    COST_PER_WEAR,
}

enum class SortDirection { ASCENDING, DESCENDING }

/** Applied client-side (`ClosetViewModel`, `feature:closet`) after the repository's
 * own filter query — sorting needs wear-stat fields (`WEAR_COUNT`/`COST_PER_WEAR`/
 * `RECENTLY_WORN`) that live in `StatsRepository`, not `GarmentRepository`, so this
 * is a screen-level join rather than something either repository can do alone. */
data class GarmentSort(
    val field: GarmentSortField = GarmentSortField.RECENTLY_ADDED,
    val direction: SortDirection = SortDirection.DESCENDING,
) {
    companion object {
        val DEFAULT = GarmentSort()
    }
}
