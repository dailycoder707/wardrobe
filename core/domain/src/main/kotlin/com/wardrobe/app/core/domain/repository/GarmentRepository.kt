package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import kotlinx.coroutines.flow.Flow

/**
 * A Paging-3-backed variant (`Flow<PagingData<Garment>>`, Phase 1 Section 21) is
 * deferred to Phase 5a/5c, once there's a real UI consuming it to design the paging
 * key against — `core:domain` stays paging-library-agnostic for now rather than take
 * on a dependency this phase can't yet verify end to end (Constitution rule 4).
 */
interface GarmentRepository {
    fun observeGarments(filter: GarmentFilter): Flow<List<Garment>>

    /** Added Phase 5c for Garment Detail — reactive, so favorite/edit/wear
     * changes made elsewhere reach an open detail screen immediately. */
    fun observeGarment(id: GarmentId): Flow<Garment?>

    suspend fun getGarment(id: GarmentId): Garment?

    /** Persists a new garment plus its already-processed images
     * (`core:image`'s pipeline output — Phase 5b). */
    suspend fun saveGarment(garment: Garment): GarmentId

    suspend fun updateGarment(garment: Garment)

    suspend fun setStatus(
        id: GarmentId,
        status: GarmentStatus,
    )

    /** Added Phase 5c for the Closet tile / Garment Detail favorite toggle. */
    suspend fun setFavorite(
        id: GarmentId,
        isFavorite: Boolean,
    )

    /** Added Phase 6 — the manual "don't recommend right now" toggle the styling
     * engine's rule filter reads (`Garment.isInLaundry`). */
    suspend fun setInLaundry(
        id: GarmentId,
        isInLaundry: Boolean,
    )

    /** Throws if the garment has wear or outfit history — RESTRICT by schema design,
     * see phase-3-persistence.md. Callers should offer [setStatus] to `SOLD`/
     * `DONATING` instead of a hard delete when that's the actual intent. */
    suspend fun deleteGarment(id: GarmentId)

    /** A warn-only, category+color metadata heuristic for the Add-to-Wardrobe
     * review screen — deliberately independent of `DuplicateGroup`'s
     * wear-history-based machinery (`WardrobeIntelligenceRepositoryImpl`),
     * which needs data (cost-per-wear) that doesn't exist yet for a garment
     * mid-creation. ACTIVE-status garments only; [excludeGarmentId] lets an
     * existing garment's own edit screen call this without matching itself. */
    suspend fun findPotentialDuplicates(
        categoryId: CategoryId,
        colorId: ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment>
}
