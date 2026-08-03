package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.dao.TagDao
import com.wardrobe.app.core.database.entity.OutfitDressCodeCrossRef
import com.wardrobe.app.core.database.entity.OutfitEntity
import com.wardrobe.app.core.database.entity.OutfitGarmentCrossRef
import com.wardrobe.app.core.database.entity.OutfitSeasonCrossRef
import com.wardrobe.app.core.database.entity.OutfitTagCrossRef
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.outfit.OutfitSource
import kotlinx.serialization.Serializable

@Serializable
private data class SlotWire(
    val layerSlot: Int,
    val garmentSyncId: String,
)

@Serializable
private data class OutfitWire(
    val name: String?,
    val occasionSyncId: String?,
    val source: OutfitSource,
    val isSaved: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val notes: String?,
    val mood: String?,
    val photoUri: String?,
    val createdAt: Long,
    val slots: List<SlotWire>,
    val seasons: List<Season>,
    val dressCodes: List<DressCode>,
    val tagSyncIds: List<String>,
)

/** Same "scalars newest-wins, collections merge" split as
 * [GarmentSyncHandler] — see that class's KDoc for the full reasoning.
 * `outfit_garments` (the slot composition) is keyed by [SlotWire.layerSlot]
 * rather than a plain set, so its merge is a keyed union (remote wins on a
 * slot both sides touched) rather than a pure set union. */
class OutfitSyncHandler(
    private val outfitDao: OutfitDao,
    private val occasionDao: OccasionDao,
    private val garmentDao: GarmentDao,
    private val tagDao: TagDao,
) : SyncEntityHandler {
    override val tableName = "outfits"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val relations = outfitDao.getWithRelationsBySyncId(syncId) ?: return null
        val outfit = relations.outfit
        val wire =
            OutfitWire(
                name = outfit.name,
                occasionSyncId = outfit.occasionId?.let { resolveOccasionSyncId(it) },
                source = outfit.source,
                isSaved = outfit.isSaved,
                isFavorite = outfit.isFavorite,
                isArchived = outfit.isArchived,
                notes = outfit.notes,
                mood = outfit.mood,
                photoUri = outfit.photoUri,
                createdAt = outfit.createdAt,
                slots =
                    relations.garments.mapNotNull { ref ->
                        garmentDao.getById(ref.garmentId)?.syncId?.let { SlotWire(ref.layerSlot, it) }
                    },
                seasons = relations.seasons.map { it.season },
                dressCodes = relations.dressCodes.map { it.dressCode },
                tagSyncIds = relations.tags.mapNotNull { tagDao.getById(it.tagId)?.syncId },
            )
        return syncJson.encodeToString(OutfitWire.serializer(), wire)
    }

    private suspend fun resolveOccasionSyncId(occasionId: Long): String? = occasionDao.getById(occasionId)?.syncId

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(OutfitWire.serializer(), fieldsJson)
        val existing = outfitDao.getBySyncId(syncId)
        val outcome = applyScalarFields(existing, wire, syncId, remoteUpdatedAt, resolver)
        val outfitId = existing?.id ?: outfitDao.getBySyncId(syncId)?.id ?: return outcome
        mergeCollections(outfitId, wire, resolver)
        return outcome
    }

    private suspend fun applyScalarFields(
        existing: OutfitEntity?,
        wire: OutfitWire,
        syncId: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        if (existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val occasionId = wire.occasionSyncId?.let { resolver.resolveLocalId("occasions", it) }
        val entity =
            OutfitEntity(
                id = existing?.id ?: 0,
                name = wire.name,
                occasionId = occasionId,
                source = wire.source,
                isSaved = wire.isSaved,
                isFavorite = wire.isFavorite,
                isArchived = wire.isArchived,
                notes = wire.notes,
                mood = wire.mood,
                photoUri = wire.photoUri,
                createdAt = wire.createdAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) outfitDao.insert(entity) else outfitDao.update(entity)
        return ApplyOutcome.Applied
    }

    private suspend fun mergeCollections(
        outfitId: Long,
        wire: OutfitWire,
        resolver: SyncIdResolver,
    ) {
        val current = outfitDao.getWithRelations(outfitId) ?: return

        val bySlot = current.garments.associateBy { it.layerSlot }.toMutableMap()
        wire.slots.forEach { slot ->
            val garmentId = resolver.resolveLocalId("garments", slot.garmentSyncId) ?: return@forEach
            bySlot[slot.layerSlot] = OutfitGarmentCrossRef(outfitId, slot.layerSlot, garmentId)
        }
        outfitDao.clearGarmentSlots(outfitId)
        outfitDao.insertGarmentSlots(bySlot.values.toList())

        val unionSeasons = (current.seasons.map { it.season } + wire.seasons).distinct()
        outfitDao.clearSeasons(outfitId)
        outfitDao.insertSeasons(unionSeasons.map { OutfitSeasonCrossRef(outfitId, it) })

        val unionDressCodes = (current.dressCodes.map { it.dressCode } + wire.dressCodes).distinct()
        outfitDao.clearDressCodes(outfitId)
        outfitDao.insertDressCodes(unionDressCodes.map { OutfitDressCodeCrossRef(outfitId, it) })

        val tagIds = current.tags.map { it.tagId }.toMutableSet()
        wire.tagSyncIds.forEach { tagSyncId -> resolver.resolveLocalId("tags", tagSyncId)?.let { tagIds.add(it) } }
        outfitDao.clearTags(outfitId)
        outfitDao.insertTags(tagIds.map { OutfitTagCrossRef(outfitId, it) })
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = outfitDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: \"${existing.name ?: "Untitled outfit"}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                outfitDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
