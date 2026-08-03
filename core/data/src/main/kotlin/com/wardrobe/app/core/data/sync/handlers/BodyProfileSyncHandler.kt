package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.entity.BodyMeasurementsEntity
import com.wardrobe.app.core.database.entity.BodyProfileEntity
import com.wardrobe.app.core.database.entity.BodyReferencePhotoEntity
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.tryon.BodyPose
import com.wardrobe.app.core.tryon.storage.BodyImageFileStore
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class BodyReferencePhotoWire(
    val pose: String,
    val width: Int,
    val height: Int,
    val checksum: String?,
)

@Serializable
private data class BodyMeasurementsWire(
    val shoulderWidthFraction: Float?,
    val torsoHeightFraction: Float?,
    val waistHeightFraction: Float?,
    val hipWidthFraction: Float?,
    val neckPositionYFraction: Float?,
    val anklePositionYFraction: Float?,
    val confidence: Float?,
    val source: String,
    val computedAt: Long,
)

@Serializable
private data class BodyProfileWire(
    val label: String,
    val createdAt: Long,
    val photos: List<BodyReferencePhotoWire>,
    val measurements: BodyMeasurementsWire?,
)

/**
 * `body_reference_photos`/`body_measurements` are **not** independently
 * sync-tracked (no `SyncEntityType` entry, no `syncId` of their own) — they
 * ride along inside this handler's own payload, the same "collections
 * ride along with their parent" convention already used for e.g. garment
 * seasons/tags. Each photo's [BodyReferencePhotoWire.checksum] still lets
 * its actual bytes go through the checksum-deduplicated image-transfer
 * phase (`runImageTransferPhase`'s Phase 10 update) exactly like a garment
 * cutout — [filePath][BodyReferencePhotoEntity.filePath] is never sent over
 * the wire, only recomputed locally via [BodyImageFileStore], the same
 * "never send a path, only a checksum" rule [ImageMetadataSyncHandler]
 * already established.
 */
class BodyProfileSyncHandler(
    private val bodyProfileDao: BodyProfileDao,
    private val bodyImageFileStore: BodyImageFileStore,
    private val imageFileStore: ImageFileStore,
) : SyncEntityHandler {
    override val tableName = "body_profiles"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = bodyProfileDao.getProfileBySyncId(syncId) ?: return null
        val photos = bodyProfileDao.getPhotos(entity.id)
        val measurements = bodyProfileDao.getMeasurements(entity.id)
        val wire =
            BodyProfileWire(
                label = entity.label,
                createdAt = entity.createdAt,
                photos = photos.map { it.toWire() },
                measurements = measurements?.toWire(),
            )
        return syncJson.encodeToString(BodyProfileWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(BodyProfileWire.serializer(), fieldsJson)
        val existing = bodyProfileDao.getProfileBySyncId(syncId)
        if (existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val profileId = upsertProfile(existing, syncId, remoteUpdatedAt, wire)
        applyPhotos(profileId, wire.photos)
        wire.measurements?.let { bodyProfileDao.upsertMeasurements(it.toEntity(profileId)) }
        return ApplyOutcome.Applied
    }

    private suspend fun upsertProfile(
        existing: BodyProfileEntity?,
        syncId: String,
        remoteUpdatedAt: Long,
        wire: BodyProfileWire,
    ): Long {
        val entity =
            BodyProfileEntity(
                id = existing?.id ?: 0,
                label = wire.label,
                createdAt = wire.createdAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) return bodyProfileDao.insertProfile(entity)
        bodyProfileDao.updateProfile(entity)
        return existing.id
    }

    private suspend fun applyPhotos(
        profileId: Long,
        photos: List<BodyReferencePhotoWire>,
    ) {
        bodyImageFileStore.ensureExists(bodyImageFileStore.profileDir(profileId))
        photos.forEach { photoWire ->
            val destination = bodyImageFileStore.photoFile(profileId, BodyPose.valueOf(photoWire.pose))
            placeFileForChecksum(photoWire.checksum, destination, imageFileStore) { checksum ->
                bodyProfileDao.getPhotoByChecksum(checksum)?.filePath?.let(::File)
            }
            bodyProfileDao.upsertPhoto(
                BodyReferencePhotoEntity(
                    bodyProfileId = profileId,
                    pose = photoWire.pose,
                    filePath = destination.absolutePath,
                    width = photoWire.width,
                    height = photoWire.height,
                    checksum = photoWire.checksum,
                ),
            )
        }
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = bodyProfileDao.getProfileBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: body profile \"${existing.label}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                bodyImageFileStore.deleteProfileDirectory(existing.id)
                bodyProfileDao.deleteProfile(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

private fun BodyReferencePhotoEntity.toWire() = BodyReferencePhotoWire(pose, width, height, checksum)

private fun BodyMeasurementsEntity.toWire() =
    BodyMeasurementsWire(
        shoulderWidthFraction,
        torsoHeightFraction,
        waistHeightFraction,
        hipWidthFraction,
        neckPositionYFraction,
        anklePositionYFraction,
        confidence,
        source,
        computedAt,
    )

private fun BodyMeasurementsWire.toEntity(bodyProfileId: Long) =
    BodyMeasurementsEntity(
        bodyProfileId = bodyProfileId,
        shoulderWidthFraction = shoulderWidthFraction,
        torsoHeightFraction = torsoHeightFraction,
        waistHeightFraction = waistHeightFraction,
        hipWidthFraction = hipWidthFraction,
        neckPositionYFraction = neckPositionYFraction,
        anklePositionYFraction = anklePositionYFraction,
        confidence = confidence,
        source = source,
        computedAt = computedAt,
    )
