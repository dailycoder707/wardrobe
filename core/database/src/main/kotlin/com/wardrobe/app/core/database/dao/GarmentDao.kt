package com.wardrobe.app.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wardrobe.app.core.database.entity.GarmentColorPaletteCrossRef
import com.wardrobe.app.core.database.entity.GarmentDressCodeCrossRef
import com.wardrobe.app.core.database.entity.GarmentEntity
import com.wardrobe.app.core.database.entity.GarmentMaterialCrossRef
import com.wardrobe.app.core.database.entity.GarmentSeasonCrossRef
import com.wardrobe.app.core.database.entity.GarmentTagCrossRef
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.Season
import kotlinx.coroutines.flow.Flow

/**
 * The `(:param IS NULL OR column = :param)` pattern below is what implements
 * `GarmentFilter`'s "null means don't filter on this" contract (core:model) as one
 * query rather than a dynamically-built string — see phase-3-persistence.md.
 */
private const val GARMENT_FILTER_WHERE = """
    WHERE (:categoryId IS NULL OR g.categoryId = :categoryId)
      AND (:brandId IS NULL OR g.brandId = :brandId)
      AND (:status IS NULL OR g.status = :status)
      AND (:isFavorite IS NULL OR g.isFavorite = :isFavorite)
      AND (
        :season IS NULL OR EXISTS (
          SELECT 1 FROM garment_seasons gs WHERE gs.garmentId = g.id AND gs.season = :season
        )
      )
      AND (
        :dressCode IS NULL OR EXISTS (
          SELECT 1 FROM garment_dress_codes gd WHERE gd.garmentId = g.id AND gd.dressCode = :dressCode
        )
      )
      AND (:searchQuery IS NULL OR g.searchText LIKE '%' || :searchQuery || '%')
"""

@Dao
interface GarmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: GarmentEntity): Long

    @Update
    suspend fun update(entity: GarmentEntity)

    /** Throws `SQLiteConstraintException` if the garment has wear/outfit history —
     * RESTRICT by design, see phase-3-persistence.md. Callers should catch this and
     * guide the user toward a status change instead (Phase 5a/5c). */
    @Query("DELETE FROM garments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    @Query("SELECT * FROM garments g WHERE g.id = :id")
    suspend fun getWithRelations(id: Long): GarmentWithRelations?

    /** Phase 8 sync — resolving a foreign key reference doesn't need the
     * full relation graph, just the row itself. */
    @Query("SELECT * FROM garments WHERE id = :id")
    suspend fun getById(id: Long): GarmentEntity?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM garments WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): GarmentEntity?

    @Transaction
    @Query("SELECT * FROM garments g WHERE g.syncId = :syncId")
    suspend fun getWithRelationsBySyncId(syncId: String): GarmentWithRelations?

    /** Added Phase 5c for Garment Detail — a `Flow` counterpart to
     * [getWithRelations] so favorite/edit/wear-history changes made elsewhere
     * reach an open Garment Detail screen without a manual refresh. */
    @Transaction
    @Query("SELECT * FROM garments g WHERE g.id = :id")
    fun observeWithRelations(id: Long): Flow<GarmentWithRelations?>

    @Suppress("LongParameterList")
    @Transaction
    @Query("SELECT * FROM garments g $GARMENT_FILTER_WHERE ORDER BY g.updatedAt DESC")
    fun observeFiltered(
        categoryId: Long?,
        brandId: Long?,
        status: GarmentStatus?,
        season: Season?,
        dressCode: DressCode?,
        searchQuery: String?,
        isFavorite: Boolean?,
    ): Flow<List<GarmentWithRelations>>

    /** Paging 3 source for Closet Browse at scale (Phase 1 Section 21: 1000+ items). */
    @Suppress("LongParameterList")
    @Transaction
    @Query("SELECT * FROM garments g $GARMENT_FILTER_WHERE ORDER BY g.updatedAt DESC")
    fun pagingSource(
        categoryId: Long?,
        brandId: Long?,
        status: GarmentStatus?,
        season: Season?,
        dressCode: DressCode?,
        searchQuery: String?,
        isFavorite: Boolean?,
    ): PagingSource<Int, GarmentWithRelations>

    /** Backs the Garment Detail/Closet Tile favorite toggle (Phase 5c) — a direct
     * column update, not a full `update(entity)` round trip, so toggling favorite
     * doesn't require the caller to have the full entity in hand. */
    @Query("UPDATE garments SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
        updatedAt: Long,
    )

    /** Backs the Phase 6 styling engine's "don't recommend right now" toggle —
     * same single-column-update shape as [setFavorite]. */
    @Query("UPDATE garments SET isInLaundry = :isInLaundry, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setInLaundry(
        id: Long,
        isInLaundry: Boolean,
        updatedAt: Long,
    )

    // --- Cross-ref management. A repository "replace the whole set" operation
    // (Phase 5a) deletes-then-inserts within one @androidx.room.Transaction spanning
    // both calls; these DAO methods are the primitives, not the transaction boundary.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPalette(entries: List<GarmentColorPaletteCrossRef>)

    @Query("DELETE FROM garment_color_palette WHERE garmentId = :garmentId")
    suspend fun clearPalette(garmentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(entries: List<GarmentMaterialCrossRef>)

    @Query("DELETE FROM garment_materials WHERE garmentId = :garmentId")
    suspend fun clearMaterials(garmentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(entries: List<GarmentTagCrossRef>)

    @Query("DELETE FROM garment_tags WHERE garmentId = :garmentId")
    suspend fun clearTags(garmentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(entries: List<GarmentSeasonCrossRef>)

    @Query("DELETE FROM garment_seasons WHERE garmentId = :garmentId")
    suspend fun clearSeasons(garmentId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDressCodes(entries: List<GarmentDressCodeCrossRef>)

    @Query("DELETE FROM garment_dress_codes WHERE garmentId = :garmentId")
    suspend fun clearDressCodes(garmentId: Long)
}
