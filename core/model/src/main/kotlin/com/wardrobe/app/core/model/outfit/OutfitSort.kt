package com.wardrobe.app.core.model.outfit

import com.wardrobe.app.core.model.garment.SortDirection

enum class OutfitSortField { RECENTLY_ADDED, RECENTLY_WORN, MOST_WORN, ALPHABETICAL }

data class OutfitSort(
    val field: OutfitSortField,
    val direction: SortDirection,
) {
    companion object {
        val DEFAULT = OutfitSort(OutfitSortField.RECENTLY_ADDED, SortDirection.DESCENDING)
    }
}
