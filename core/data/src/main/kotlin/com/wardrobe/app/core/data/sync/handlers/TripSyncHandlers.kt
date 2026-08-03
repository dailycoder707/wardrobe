package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.TripDao
import com.wardrobe.app.core.database.entity.PackingListItemEntity
import com.wardrobe.app.core.database.entity.TripActivityEntity
import com.wardrobe.app.core.database.entity.TripEntity
import com.wardrobe.app.core.model.trip.LuggageSize
import kotlinx.serialization.Serializable

@Serializable
private data class TripWire(
    val name: String?,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val luggageSize: LuggageSize?,
    val createdAt: Long,
)

class TripSyncHandler(
    private val tripDao: TripDao,
) : SyncEntityHandler {
    override val tableName = "trips"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = tripDao.getBySyncId(syncId) ?: return null
        val wire =
            TripWire(
                name = entity.name,
                destination = entity.destination,
                startDate = entity.startDate,
                endDate = entity.endDate,
                luggageSize = entity.luggageSize,
                createdAt = entity.createdAt,
            )
        return syncJson.encodeToString(TripWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(TripWire.serializer(), fieldsJson)
        val existing = tripDao.getBySyncId(syncId)
        if (existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val entity =
            TripEntity(
                id = existing?.id ?: 0,
                name = wire.name,
                destination = wire.destination,
                startDate = wire.startDate,
                endDate = wire.endDate,
                luggageSize = wire.luggageSize,
                createdAt = wire.createdAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) tripDao.insert(entity) else tripDao.update(entity)
        return ApplyOutcome.Applied
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = tripDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: trip to ${existing.destination}",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                tripDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class TripActivityWire(
    val tripSyncId: String,
    val activityTag: String,
)

class TripActivitySyncHandler(
    private val tripDao: TripDao,
) : SyncEntityHandler {
    override val tableName = "trip_activities"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = tripDao.getActivityBySyncId(syncId) ?: return null
        val tripSyncId = tripDao.getById(entity.tripId)?.syncId
        return tripSyncId?.let {
            syncJson.encodeToString(TripActivityWire.serializer(), TripActivityWire(it, entity.activityTag))
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(TripActivityWire.serializer(), fieldsJson)
        val existing = tripDao.getActivityBySyncId(syncId)
        val tripId = resolver.resolveLocalId("trips", wire.tripSyncId)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (tripId != null && !localIsNewerOrEqual) {
            val entity =
                TripActivityEntity(
                    id = existing?.id ?: 0,
                    tripId = tripId,
                    activityTag = wire.activityTag,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) tripDao.insertActivity(entity) else tripDao.updateActivity(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = tripDao.getActivityBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: activity \"${existing.activityTag}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                tripDao.deleteActivityById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class PackingListItemWire(
    val tripSyncId: String,
    val garmentSyncId: String?,
    val freeTextName: String?,
    val category: String?,
    val isPacked: Boolean,
    val rationale: String?,
)

class PackingListItemSyncHandler(
    private val tripDao: TripDao,
    private val garmentDao: GarmentDao,
) : SyncEntityHandler {
    override val tableName = "packing_list_items"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = tripDao.getPackingListItemBySyncId(syncId) ?: return null
        val tripSyncId = tripDao.getById(entity.tripId)?.syncId
        return tripSyncId?.let {
            syncJson.encodeToString(
                PackingListItemWire.serializer(),
                PackingListItemWire(
                    tripSyncId = it,
                    garmentSyncId = entity.garmentId?.let { garmentId -> garmentDao.getById(garmentId)?.syncId },
                    freeTextName = entity.freeTextName,
                    category = entity.category,
                    isPacked = entity.isPacked,
                    rationale = entity.rationale,
                ),
            )
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(PackingListItemWire.serializer(), fieldsJson)
        val existing = tripDao.getPackingListItemBySyncId(syncId)
        val tripId = resolver.resolveLocalId("trips", wire.tripSyncId)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (tripId != null && !localIsNewerOrEqual) {
            val garmentId = wire.garmentSyncId?.let { resolver.resolveLocalId("garments", it) }
            val entity =
                PackingListItemEntity(
                    id = existing?.id ?: 0,
                    tripId = tripId,
                    garmentId = garmentId,
                    freeTextName = wire.freeTextName,
                    category = wire.category,
                    isPacked = wire.isPacked,
                    rationale = wire.rationale,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) tripDao.insertPackingListItem(entity) else tripDao.updatePackingListItem(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = tripDao.getPackingListItemBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: packing item \"${existing.freeTextName ?: "item"}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                tripDao.deletePackingListItem(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
