package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.entity.OutfitDressCodeCrossRef
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.OutfitSeasonCrossRef
import com.wardrobe.app.core.database.entity.OutfitTagCrossRef
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OutfitRepositoryImpl
    @Inject
    constructor(
        private val dao: OutfitDao,
    ) : OutfitRepository {
        override fun observeOutfits(filter: OutfitFilter): Flow<List<Outfit>> =
            dao
                .observeFiltered(
                    isSaved = filter.isSaved,
                    isArchived = filter.isArchived,
                    isFavorite = filter.isFavorite,
                    occasionId = filter.occasionId?.value,
                    searchQuery =
                        filter.searchQuery
                            ?.lowercase()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                    season = filter.season,
                    dressCode = filter.dressCode,
                    tagId = filter.tagId?.value,
                ).map { rows -> rows.map { it.toDomain() } }

        override fun observeOutfit(id: OutfitId): Flow<Outfit?> =
            dao.observeWithRelations(id.value).map { it?.toDomain() }

        override suspend fun getOutfit(id: OutfitId): Outfit? = dao.getWithRelations(id.value)?.toDomain()

        /** `outfit.id.value == 0L` (core:model's convention for "not yet persisted,"
         * matching `GarmentRepositoryImpl`) inserts a new row; any other id updates the
         * existing one in place — Phase 3's interface has one method for both, unlike
         * Garment's separate save/update, since an outfit's "edit" flow (Outfit Builder's
         * Restyle) is expected to re-save the whole outfit wholesale rather than patch
         * individual fields. */
        override suspend fun saveOutfit(outfit: Outfit): OutfitId {
            val entity = outfit.toEntity()
            val now = System.currentTimeMillis()
            val id =
                if (outfit.id.value == 0L) {
                    dao.insert(
                        entity.copy(
                            id = 0,
                            syncId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            updatedAt = now,
                        ),
                    )
                } else {
                    // Phase 8: syncId must survive an edit unchanged — Outfit.toEntity()
                    // has no way to know it, so it's read from the row being replaced.
                    val existingSyncId = dao.getById(outfit.id.value)?.syncId.orEmpty()
                    dao.update(entity.copy(syncId = existingSyncId, updatedAt = now))
                    outfit.id.value
                }
            writeCrossRefs(id, outfit)
            return OutfitId(id)
        }

        override suspend fun setFavorite(
            id: OutfitId,
            isFavorite: Boolean,
        ) = dao.setFavorite(id.value, isFavorite)

        override suspend fun setArchived(
            id: OutfitId,
            isArchived: Boolean,
        ) = dao.setArchived(id.value, isArchived)

        /** Throws `SQLiteConstraintException` if the outfit has wear history — RESTRICT
         * by schema design, same reasoning as `GarmentRepositoryImpl.deleteGarment`. */
        override suspend fun deleteOutfit(id: OutfitId) = dao.deleteById(id.value)

        private suspend fun writeCrossRefs(
            outfitId: Long,
            outfit: Outfit,
        ) {
            dao.clearGarmentSlots(outfitId)
            if (outfit.garments.isNotEmpty()) {
                dao.insertGarmentSlots(
                    outfit.garments.map { slot ->
                        OutfitGarmentCrossRef(outfitId, slot.layerSlot, slot.garmentId.value)
                    },
                )
            }
            dao.clearSeasons(outfitId)
            if (outfit.seasons.isNotEmpty()) {
                dao.insertSeasons(outfit.seasons.map { OutfitSeasonCrossRef(outfitId, it) })
            }
            dao.clearDressCodes(outfitId)
            if (outfit.dressCodes.isNotEmpty()) {
                dao.insertDressCodes(outfit.dressCodes.map { OutfitDressCodeCrossRef(outfitId, it) })
            }
            dao.clearTags(outfitId)
            if (outfit.tagIds.isNotEmpty()) {
                dao.insertTags(outfit.tagIds.map { OutfitTagCrossRef(outfitId, it.value) })
            }
        }
    }
