package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentMaskDao
import com.wardrobe.app.core.database.entity.GarmentMaskEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class GarmentMaskWire(
    val bodyProfileSyncId: String,
    val garmentSyncId: String,
    val checksum: String?,
)

/** A manual erase/restore mask (see `core:model`'s `GarmentMask` KDoc) —
 * its image bytes go through the same checksum-deduplicated transfer as a
 * garment cutout or body reference photo, its path never sent over the
 * wire, only recomputed locally via [BodyImageFileStore]. */
class GarmentMaskSyncHandler(
    private val garmentMaskDao: GarmentMaskDao,
    private val bodyProfileDao: BodyProfileDao,
    private val garmentDao: GarmentDao,
    private val bodyImageFileStore: BodyImageFileStore,
    private val imageFileStore: ImageFileStore,
) : SyncEntityHandler {
    override val tableName = "garment_masks"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = garmentMaskDao.getBySyncId(syncId) ?: return null
        val bodyProfileSyncId = bodyProfileDao.getProfileById(entity.bodyProfileId)?.syncId
        val garmentSyncId = garmentDao.getById(entity.garmentId)?.syncId
        return if (bodyProfileSyncId == null || garmentSyncId == null) {
            null
        } else {
            val wire = GarmentMaskWire(bodyProfileSyncId, garmentSyncId, entity.checksum)
            syncJson.encodeToString(GarmentMaskWire.serializer(), wire)
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(GarmentMaskWire.serializer(), fieldsJson)
        val existing = garmentMaskDao.getBySyncId(syncId)
        val bodyProfileId = resolver.resolveLocalId("body_profiles", wire.bodyProfileSyncId)
        val garmentId = resolver.resolveLocalId("garments", wire.garmentSyncId)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (bodyProfileId == null || garmentId == null || localIsNewerOrEqual) {
            return ApplyOutcome.LocalNewerIgnored
        }
        bodyImageFileStore.ensureExists(bodyImageFileStore.masksDir(bodyProfileId))
        val destination = bodyImageFileStore.maskFile(bodyProfileId, garmentId)
        placeFileForChecksum(wire.checksum, destination, imageFileStore) { checksum ->
            garmentMaskDao.getByChecksum(checksum)?.maskFilePath?.let(::File)
        }
        val entity =
            GarmentMaskEntity(
                id = existing?.id ?: 0,
                bodyProfileId = bodyProfileId,
                garmentId = garmentId,
                maskFilePath = destination.absolutePath,
                checksum = wire.checksum,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) garmentMaskDao.upsert(entity) else garmentMaskDao.upsert(entity.copy(id = existing.id))
        return ApplyOutcome.Applied
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = garmentMaskDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: a garment mask",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                garmentMaskDao.clear(existing.bodyProfileId, existing.garmentId)
                ApplyOutcome.Applied
            }
        }
    }
}
