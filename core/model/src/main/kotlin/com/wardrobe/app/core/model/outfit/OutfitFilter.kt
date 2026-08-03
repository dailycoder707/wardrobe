package com.wardrobe.app.core.model.outfit

import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season

/**
 * Every field left `null`/`false`/empty means "don't filter on this," mirroring
 * `GarmentFilter`'s contract. [occasionId]/[isSaved]/[isArchived]/[isFavorite]/
 * [searchQuery] are pushed to SQL (`OutfitDao`); [season]/[dressCode]/[tagId]
 * are applied in `OutfitRepositoryImpl` after the DB query, over the same
 * already-materialized-list rationale `GarmentFilter` documents — outfit counts
 * at this app's scale (Phase 5d target: 500+ saved outfits) make an in-memory
 * pass over the mapped `Outfit` list cheap.
 */
data class OutfitFilter(
    val occasionId: OccasionId? = null,
    val isSaved: Boolean? = true,
    val isArchived: Boolean? = false,
    val isFavorite: Boolean? = null,
    val searchQuery: String? = null,
    val season: Season? = null,
    val dressCode: DressCode? = null,
    val tagId: TagId? = null,
)
