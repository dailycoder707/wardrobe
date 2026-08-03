package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.BodyProfileDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.GarmentPlacementTemplateDao
import com.wardrobe.app.core.database.entity.GarmentPlacementTemplateEntity
import kotlinx.serialization.Serializable

@Serializable
private data class GarmentPlacementTemplateWire(
    val bodyProfileSyncId: String,
    val garmentSyncId: String,
    val templateType: String,
    val customName: String?,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val isUserAdjusted: Boolean,
    val placementSource: String,
    val lastUsedAt: Long?,
)

/** Real user effort worth syncing between the user's own paired devices —
 * one saved affine placement per `(bodyProfile, garment, templateType,
 * customName)`, never a warp/mesh (Constitution rule 13/ADR-011's
 * technical-approach constraint applies equally to what gets synced). */
class GarmentPlacementTemplateSyncHandler(
    private val templateDao: GarmentPlacementTemplateDao,
    private val bodyProfileDao: BodyProfileDao,
    private val garmentDao: GarmentDao,
) : SyncEntityHandler {
    override val tableName = "garment_placement_templates"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = templateDao.getBySyncId(syncId) ?: return null
        val bodyProfileSyncId = bodyProfileDao.getProfileById(entity.bodyProfileId)?.syncId
        val garmentSyncId = garmentDao.getById(entity.garmentId)?.syncId
        return if (bodyProfileSyncId == null || garmentSyncId == null) {
            null
        } else {
            val wire =
                GarmentPlacementTemplateWire(
                    bodyProfileSyncId = bodyProfileSyncId,
                    garmentSyncId = garmentSyncId,
                    templateType = entity.templateType,
                    customName = entity.customName,
                    offsetXFraction = entity.offsetXFraction,
                    offsetYFraction = entity.offsetYFraction,
                    scale = entity.scale,
                    rotationDegrees = entity.rotationDegrees,
                    isUserAdjusted = entity.isUserAdjusted,
                    placementSource = entity.placementSource,
                    lastUsedAt = entity.lastUsedAt,
                )
            syncJson.encodeToString(GarmentPlacementTemplateWire.serializer(), wire)
        }
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(GarmentPlacementTemplateWire.serializer(), fieldsJson)
        val existing = templateDao.getBySyncId(syncId)
        val bodyProfileId = resolver.resolveLocalId("body_profiles", wire.bodyProfileSyncId)
        val garmentId = resolver.resolveLocalId("garments", wire.garmentSyncId)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (bodyProfileId == null || garmentId == null || localIsNewerOrEqual) {
            return ApplyOutcome.LocalNewerIgnored
        }
        val entity =
            GarmentPlacementTemplateEntity(
                id = existing?.id ?: 0,
                bodyProfileId = bodyProfileId,
                garmentId = garmentId,
                templateType = wire.templateType,
                customName = wire.customName,
                offsetXFraction = wire.offsetXFraction,
                offsetYFraction = wire.offsetYFraction,
                scale = wire.scale,
                rotationDegrees = wire.rotationDegrees,
                isUserAdjusted = wire.isUserAdjusted,
                placementSource = wire.placementSource,
                lastUsedAt = wire.lastUsedAt,
                updatedAt = remoteUpdatedAt,
                syncId = syncId,
            )
        if (existing == null) templateDao.insert(entity) else templateDao.update(entity)
        return ApplyOutcome.Applied
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = templateDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: a saved try-on placement",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                templateDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
