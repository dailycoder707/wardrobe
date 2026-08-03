package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

/** The Closet screen's user-facing filter selections — a superset of what
 * `GarmentFilter` (core:model) expresses, since `neverWorn`/`recentlyWorn`
 * need a wear-stats join `GarmentFilter` deliberately doesn't own (see that
 * class's doc comment). [activeCount] backs the "N filters active" affordance
 * / one-tap-clear button in the toolbar. */
data class ClosetFilterState(
    val category: CategoryId? = null,
    val color: ColorId? = null,
    val brand: BrandId? = null,
    val material: MaterialId? = null,
    val season: Season? = null,
    val dressCode: DressCode? = null,
    val tag: TagId? = null,
    val favoriteOnly: Boolean = false,
    val neverWorn: Boolean = false,
    val recentlyWornOnly: Boolean = false,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
) {
    val activeCount: Int
        get() =
            listOfNotNull(
                category,
                color,
                brand,
                material,
                season,
                dressCode,
                tag,
                priceMin,
                priceMax,
            ).size + listOf(favoriteOnly, neverWorn, recentlyWornOnly).count { it }

    companion object {
        val EMPTY = ClosetFilterState()
    }
}
