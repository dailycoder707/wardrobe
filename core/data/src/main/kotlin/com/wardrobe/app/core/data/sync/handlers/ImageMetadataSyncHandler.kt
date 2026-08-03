package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.ImageMetadataDao
import com.wardrobe.app.core.database.entity.ImageMetadataEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.garment.ImageType
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class ImageMetadataWire(
    val garmentSyncId: String,
    val type: ImageType,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: String,
    val checksum: String?,
    val createdAt: Long,
)

/**
 * [filePath] is deliberately never sent over the wire — each device's
 * `images/<garmentId>/...` layout is keyed by its *own* local garment id
 * (see `ImageFileStore`), which will usually differ from the sender's. This
 * handler recomputes the local path from the already-resolved local
 * garment id, and assumes the actual bytes are already on disk at that path
 * by the time [applyUpsert] runs — `SyncEngine` guarantees this by running
 * its image-transfer phase (checksum manifest exchange, whole-file copy)
 * *before* applying any `image_metadata` change. See
 * `phase-8-multi-device-sync.md`'s "Image synchronization" section.
 */
class ImageMetadataSyncHandler(
    private val imageMetadataDao: ImageMetadataDao,
    private val garmentDao: GarmentDao,
    private val imageFileStore: ImageFileStore,
) : SyncEntityHandler {
    override val tableName = "image_metadata"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = imageMetadataDao.getBySyncId(syncId)
        val garmentSyncId = entity?.let { garmentDao.getById(it.garmentId)?.syncId }
        return garmentSyncId?.let {
            val wire =
                ImageMetadataWire(
                    garmentSyncId = it,
                    type = entity.type,
                    width = entity.width,
                    height = entity.height,
                    fileSizeBytes = entity.fileSizeBytes,
                    format = entity.format,
                    checksum = entity.checksum,
                    createdAt = entity.createdAt,
                )
            syncJson.encodeToString(ImageMetadataWire.serializer(), wire)
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(ImageMetadataWire.serializer(), fieldsJson)
        val existing = imageMetadataDao.getBySyncId(syncId)
        val garmentId = resolver.resolveLocalId("garments", wire.garmentSyncId)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (garmentId != null && !localIsNewerOrEqual) {
            val garmentDir = imageFileStore.ensureExists(imageFileStore.garmentDir(garmentId))
            val localFile = imageFileStore.fileFor(garmentDir, wire.type)
            placeFileForChecksum(wire.checksum, localFile, imageFileStore) { checksum ->
                imageMetadataDao.getByChecksum(checksum)?.filePath?.let(::File)
            }
            val entity =
                ImageMetadataEntity(
                    id = existing?.id ?: 0,
                    garmentId = garmentId,
                    type = wire.type,
                    filePath = localFile.absolutePath,
                    width = wire.width,
                    height = wire.height,
                    fileSizeBytes = wire.fileSizeBytes,
                    format = wire.format,
                    checksum = wire.checksum,
                    createdAt = wire.createdAt,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) imageMetadataDao.insert(entity) else updateMetadata(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    private suspend fun updateMetadata(entity: ImageMetadataEntity) {
        imageMetadataDao.deleteById(entity.id)
        imageMetadataDao.insert(entity.copy(id = 0))
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = imageMetadataDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "A photo was replaced on this device",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                imageMetadataDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
