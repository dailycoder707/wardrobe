package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.FabricId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.WaterproofLevel

/** The Closet screen's user-facing filter selections. Every facet is a
 * [Set] — selections *within* one facet are OR'd (e.g. Color = Black OR
 * White), selections *across* different facets are AND'd (e.g. (Black OR
 * White) AND Winter AND Work) — see [com.wardrobe.app.feature.closet.closet.matchesClosetFilters]
 * for the exact evaluation and its tests for the AND/OR contract. An empty
 * set means "don't filter on this facet." [activeCount] backs the "N
 * filters active" affordance / one-tap-clear button in the toolbar. */
data class ClosetFilterState(
    val categories: Set<CategoryId> = emptySet(),
    val colors: Set<ColorId> = emptySet(),
    val brands: Set<BrandId> = emptySet(),
    val materials: Set<MaterialId> = emptySet(),
    val fabrics: Set<FabricId> = emptySet(),
    val seasons: Set<Season> = emptySet(),
    val dressCodes: Set<DressCode> = emptySet(),
    val occasions: Set<OccasionId> = emptySet(),
    val tags: Set<TagId> = emptySet(),
    val fits: Set<Fit> = emptySet(),
    val genders: Set<GarmentGender> = emptySet(),
    val waterproofLevels: Set<WaterproofLevel> = emptySet(),
    val favoriteOnly: Boolean = false,
    val neverWorn: Boolean = false,
    val recentlyWornOnly: Boolean = false,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
) {
    val activeCount: Int
        get() =
            categories.size + colors.size + brands.size + materials.size + fabrics.size +
                seasons.size + dressCodes.size + occasions.size + tags.size + fits.size +
                genders.size + waterproofLevels.size +
                listOfNotNull(priceMin, priceMax).size +
                listOf(favoriteOnly, neverWorn, recentlyWornOnly).count { it }

    companion object {
        val EMPTY = ClosetFilterState()
    }
}

/** Toggling membership in a multi-select [Set] — present values are removed,
 * absent values are added. */
internal fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
