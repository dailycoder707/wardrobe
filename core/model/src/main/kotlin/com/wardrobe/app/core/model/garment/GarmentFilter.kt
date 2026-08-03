package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.TagId

/**
 * Every field left `null`/`false`/empty means "don't filter on this." The
 * `categoryId`/`brandId`/`status`/`season`/`dressCode`/`searchQuery`/[isFavorite]
 * fields are pushed down to SQL (`GarmentDao`'s `(:param IS NULL OR ...)`
 * pattern, phase-3-persistence.md). [colorId]/[materialId]/[tagId]/price range
 * are applied in `GarmentRepositoryImpl` after the DB query, in-memory over the
 * already-materialized `Garment` list (which already carries palette/materials/
 * tagIds/price) — a deliberate Phase 5c scope decision (see
 * phase-5c-wardrobe-experience.md): this app's realistic scale (hundreds, not
 * millions, of garments) makes a linear in-memory filter pass cheap, and avoids
 * extending the SQL `WHERE` clause for filters combined freely with no single
 * obvious query shape. [neverWorn]/[recentlyWornWithinDays] are applied one
 * layer up, in `ClosetViewModel` (`feature:closet`) — they need `StatsRepository`
 * wear-history data that `GarmentRepository` deliberately has no dependency on.
 */
data class GarmentFilter(
    val categoryId: CategoryId? = null,
    val brandId: BrandId? = null,
    val status: GarmentStatus? = GarmentStatus.ACTIVE,
    val season: Season? = null,
    val dressCode: DressCode? = null,
    val searchQuery: String? = null,
    val isFavorite: Boolean? = null,
    val colorId: ColorId? = null,
    val materialId: MaterialId? = null,
    val tagId: TagId? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val neverWorn: Boolean = false,
    val recentlyWornWithinDays: Int? = null,
)
