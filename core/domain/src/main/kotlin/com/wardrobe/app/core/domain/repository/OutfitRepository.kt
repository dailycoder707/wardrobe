package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import kotlinx.coroutines.flow.Flow

interface OutfitRepository {
    fun observeOutfits(filter: OutfitFilter = OutfitFilter()): Flow<List<Outfit>>

    /** A `Flow` counterpart to [getOutfit] so Outfit Detail (Phase 5d) reflects
     * favorite/edit/wear-history changes made elsewhere without a manual
     * refresh — mirrors `GarmentRepository.observeGarment`. */
    fun observeOutfit(id: OutfitId): Flow<Outfit?>

    suspend fun getOutfit(id: OutfitId): Outfit?

    suspend fun saveOutfit(outfit: Outfit): OutfitId

    suspend fun setFavorite(
        id: OutfitId,
        isFavorite: Boolean,
    )

    suspend fun setArchived(
        id: OutfitId,
        isArchived: Boolean,
    )

    /** Throws if the outfit has wear history — RESTRICT by schema design, same
     * reasoning as [GarmentRepository.deleteGarment]. */
    suspend fun deleteOutfit(id: OutfitId)
}
