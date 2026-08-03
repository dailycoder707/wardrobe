package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wardrobe.app.core.database.entity.OutfitDressCodeCrossRef
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.OutfitSeasonCrossRef
import com.wardrobe.app.core.database.entity.OutfitTagCrossRef
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import kotlinx.coroutines.flow.Flow

/** Mirrors `GarmentDao.GARMENT_FILTER_WHERE`'s `(:param IS NULL OR ...)` pattern —
 * see that class's KDoc. `o.name` is the only text column an outfit has to
 * search against (no denormalized search text like `Garment.searchText`, since
 * an outfit doesn't carry as many describable attributes as a garment). */
private const val OUTFIT_FILTER_WHERE = """
    WHERE (:isSaved IS NULL OR o.isSaved = :isSaved)
      AND (:isArchived IS NULL OR o.isArchived = :isArchived)
      AND (:isFavorite IS NULL OR o.isFavorite = :isFavorite)
      AND (:occasionId IS NULL OR o.occasionId = :occasionId)
      AND (:searchQuery IS NULL OR o.name LIKE '%' || :searchQuery || '%')
      AND (
        :season IS NULL OR EXISTS (
          SELECT 1 FROM outfit_seasons os WHERE os.outfitId = o.id AND os.season = :season
        )
      )
      AND (
        :dressCode IS NULL OR EXISTS (
          SELECT 1 FROM outfit_dress_codes odc WHERE odc.outfitId = o.id AND odc.dressCode = :dressCode
        )
      )
      AND (
        :tagId IS NULL OR EXISTS (
          SELECT 1 FROM outfit_tags ot WHERE ot.outfitId = o.id AND ot.tagId = :tagId
        )
      )
"""

@Dao
interface OutfitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OutfitEntity): Long

    @Update
    suspend fun update(entity: OutfitEntity)

    @Query("DELETE FROM outfits WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Phase 8 sync — resolving a `wear_events.outfitId`/`feedback.targetOutfitId`
     * reference doesn't need the full relation graph, just the row itself. */
    @Query("SELECT * FROM outfits WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): OutfitEntity?

    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getById(id: Long): OutfitEntity?

    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :id")
    suspend fun getWithRelations(id: Long): OutfitWithRelations?

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Transaction
    @Query("SELECT * FROM outfits WHERE syncId = :syncId")
    suspend fun getWithRelationsBySyncId(syncId: String): OutfitWithRelations?

    @Transaction
    @Query("SELECT * FROM outfits WHERE id = :id")
    fun observeWithRelations(id: Long): Flow<OutfitWithRelations?>

    @Suppress("LongParameterList")
    @Transaction
    @Query("SELECT * FROM outfits o $OUTFIT_FILTER_WHERE ORDER BY o.createdAt DESC")
    fun observeFiltered(
        isSaved: Boolean?,
        isArchived: Boolean?,
        isFavorite: Boolean?,
        occasionId: Long?,
        searchQuery: String?,
        season: Season?,
        dressCode: DressCode?,
        tagId: Long?,
    ): Flow<List<OutfitWithRelations>>

    @Query("UPDATE outfits SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    )

    @Query("UPDATE outfits SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(
        id: Long,
        isArchived: Boolean,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGarmentSlots(slots: List<OutfitGarmentCrossRef>)

    @Query("DELETE FROM outfit_garments WHERE outfitId = :outfitId")
    suspend fun clearGarmentSlots(outfitId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(entries: List<OutfitSeasonCrossRef>)

    @Query("DELETE FROM outfit_seasons WHERE outfitId = :outfitId")
    suspend fun clearSeasons(outfitId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDressCodes(entries: List<OutfitDressCodeCrossRef>)

    @Query("DELETE FROM outfit_dress_codes WHERE outfitId = :outfitId")
    suspend fun clearDressCodes(outfitId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(entries: List<OutfitTagCrossRef>)

    @Query("DELETE FROM outfit_tags WHERE outfitId = :outfitId")
    suspend fun clearTags(outfitId: Long)

    /** Every outfit that includes [garmentId] — cost-per-wear needs this to count
     * wears of outfits containing a garment, not just direct wear events. */
    @Query("SELECT DISTINCT outfitId FROM outfit_garments WHERE garmentId = :garmentId")
    suspend fun outfitIdsContainingGarment(garmentId: Long): List<Long>
}
