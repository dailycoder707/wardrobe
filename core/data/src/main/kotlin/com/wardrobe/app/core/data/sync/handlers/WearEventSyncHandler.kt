package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.OccasionDao
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.dao.WearEventDao
import com.wardrobe.app.core.database.entity.WearEventEntity
import com.wardrobe.app.core.model.wear.WearEventStatus
import kotlinx.serialization.Serializable

@Serializable
private data class WearEventWire(
    val date: String,
    val garmentSyncId: String?,
    val outfitSyncId: String?,
    val occasionSyncId: String?,
    val note: String?,
    val status: WearEventStatus,
    val createdAt: Long,
)

/** [weatherCacheId] is deliberately dropped, not synced — it references a
 * device-local weather forecast row that has no meaning (and usually no
 * matching row at all) on the other device; see
 * `phase-8-multi-device-sync.md`'s "What does NOT sync" section. */
class WearEventSyncHandler(
    private val wearEventDao: WearEventDao,
    private val garmentDao: GarmentDao,
    private val outfitDao: OutfitDao,
    private val occasionDao: OccasionDao,
) : SyncEntityHandler {
    override val tableName = "wear_events"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = wearEventDao.getBySyncId(syncId) ?: return null
        val wire =
            WearEventWire(
                date = entity.date,
                garmentSyncId = entity.garmentId?.let { garmentDao.getById(it)?.syncId },
                outfitSyncId = entity.outfitId?.let { outfitDao.getById(it)?.syncId },
                occasionSyncId = entity.occasionId?.let { occasionDao.getById(it)?.syncId },
                note = entity.note,
                status = entity.status,
                createdAt = entity.createdAt,
            )
        return syncJson.encodeToString(WearEventWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(WearEventWire.serializer(), fieldsJson)
        val existing = wearEventDao.getBySyncId(syncId)
        val garmentId = wire.garmentSyncId?.let { resolver.resolveLocalId("garments", it) }
        val outfitId = wire.outfitSyncId?.let { resolver.resolveLocalId("outfits", it) }
        val occasionId = wire.occasionSyncId?.let { resolver.resolveLocalId("occasions", it) }
        val referencesUnresolved =
            (wire.garmentSyncId != null && garmentId == null) || (wire.outfitSyncId != null && outfitId == null)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (!referencesUnresolved && !localIsNewerOrEqual) {
            val entity =
                WearEventEntity(
                    id = existing?.id ?: 0,
                    date = wire.date,
                    garmentId = garmentId,
                    outfitId = outfitId,
                    weatherCacheId = null,
                    occasionId = occasionId,
                    note = wire.note,
                    status = wire.status,
                    createdAt = wire.createdAt,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) wearEventDao.insert(entity) else wearEventDao.update(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = wearEventDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: wear entry for ${existing.date}",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                wearEventDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
