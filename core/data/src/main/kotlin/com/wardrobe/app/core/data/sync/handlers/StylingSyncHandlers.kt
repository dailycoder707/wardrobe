package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.data.sync.ApplyOutcome
import com.wardrobe.app.core.data.sync.SyncEntityHandler
import com.wardrobe.app.core.data.sync.SyncIdResolver
import com.wardrobe.app.core.database.dao.FeedbackDao
import com.wardrobe.app.core.database.dao.GarmentDao
import com.wardrobe.app.core.database.dao.OutfitDao
import com.wardrobe.app.core.database.dao.StyleRuleDao
import com.wardrobe.app.core.database.entity.FeedbackEntity
import com.wardrobe.app.core.database.entity.StyleRuleEntity
import com.wardrobe.app.core.model.styling.FeedbackTargetType
import com.wardrobe.app.core.model.styling.FeedbackVote
import com.wardrobe.app.core.model.styling.StyleRuleSourceType
import com.wardrobe.app.core.model.styling.StyleRuleType
import kotlinx.serialization.Serializable

@Serializable
private data class StyleRuleWire(
    val description: String,
    val sourceType: StyleRuleSourceType,
    val sourceFeedbackSyncId: String?,
    val ruleType: StyleRuleType,
    val parametersJson: String,
    val isActive: Boolean,
    val createdAt: Long,
)

/** [StyleRuleEntity]/[FeedbackEntity] reference each other
 * (`sourceFeedbackId` / `generatedStyleRuleId`) — whichever arrives first in
 * a batch simply can't resolve the other's id yet, so both handlers defer
 * (`LocalNewerIgnored`, not an error) rather than fail; the un-acked change
 * log entry means the next sync retries automatically once the referenced
 * row exists locally. */
class StyleRuleSyncHandler(
    private val styleRuleDao: StyleRuleDao,
    private val feedbackDao: FeedbackDao,
) : SyncEntityHandler {
    override val tableName = "style_rules"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = styleRuleDao.getBySyncId(syncId) ?: return null
        val wire =
            StyleRuleWire(
                description = entity.description,
                sourceType = entity.sourceType,
                sourceFeedbackSyncId = entity.sourceFeedbackId?.let { feedbackDao.getById(it)?.syncId },
                ruleType = entity.ruleType,
                parametersJson = entity.parametersJson,
                isActive = entity.isActive,
                createdAt = entity.createdAt,
            )
        return syncJson.encodeToString(StyleRuleWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(StyleRuleWire.serializer(), fieldsJson)
        val existing = styleRuleDao.getBySyncId(syncId)
        val sourceFeedbackId = wire.sourceFeedbackSyncId?.let { resolver.resolveLocalId("feedback", it) }
        val referenceUnresolved = wire.sourceFeedbackSyncId != null && sourceFeedbackId == null
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (!referenceUnresolved && !localIsNewerOrEqual) {
            val entity =
                StyleRuleEntity(
                    id = existing?.id ?: 0,
                    description = wire.description,
                    sourceType = wire.sourceType,
                    sourceFeedbackId = sourceFeedbackId,
                    ruleType = wire.ruleType,
                    parametersJson = wire.parametersJson,
                    isActive = wire.isActive,
                    createdAt = wire.createdAt,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) styleRuleDao.insert(entity) else styleRuleDao.update(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = styleRuleDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: \"${existing.description}\"",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                styleRuleDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}

@Serializable
private data class FeedbackWire(
    val targetType: FeedbackTargetType,
    val targetGarmentSyncId: String?,
    val targetOutfitSyncId: String?,
    val vote: FeedbackVote,
    val reasonCode: String?,
    val reasonText: String?,
    val generatedStyleRuleSyncId: String?,
    val createdAt: Long,
)

class FeedbackSyncHandler(
    private val feedbackDao: FeedbackDao,
    private val garmentDao: GarmentDao,
    private val outfitDao: OutfitDao,
    private val styleRuleDao: StyleRuleDao,
) : SyncEntityHandler {
    override val tableName = "feedback"

    override suspend fun currentFieldsJson(syncId: String): String? {
        val entity = feedbackDao.getBySyncId(syncId) ?: return null
        val wire =
            FeedbackWire(
                targetType = entity.targetType,
                targetGarmentSyncId = entity.targetGarmentId?.let { garmentDao.getById(it)?.syncId },
                targetOutfitSyncId = entity.targetOutfitId?.let { outfitDao.getById(it)?.syncId },
                vote = entity.vote,
                reasonCode = entity.reasonCode,
                reasonText = entity.reasonText,
                generatedStyleRuleSyncId = entity.generatedStyleRuleId?.let { styleRuleDao.getById(it)?.syncId },
                createdAt = entity.createdAt,
            )
        return syncJson.encodeToString(FeedbackWire.serializer(), wire)
    }

    override suspend fun applyUpsert(
        syncId: String,
        fieldsJson: String,
        remoteUpdatedAt: Long,
        resolver: SyncIdResolver,
    ): ApplyOutcome {
        val wire = syncJson.decodeFromString(FeedbackWire.serializer(), fieldsJson)
        val existing = feedbackDao.getBySyncId(syncId)
        val targetGarmentId = wire.targetGarmentSyncId?.let { resolver.resolveLocalId("garments", it) }
        val targetOutfitId = wire.targetOutfitSyncId?.let { resolver.resolveLocalId("outfits", it) }
        val generatedStyleRuleId = wire.generatedStyleRuleSyncId?.let { resolver.resolveLocalId("style_rules", it) }
        val referencesUnresolved =
            (wire.targetGarmentSyncId != null && targetGarmentId == null) ||
                (wire.targetOutfitSyncId != null && targetOutfitId == null)
        val localIsNewerOrEqual = existing != null && !isRemoteNewer(existing.updatedAt, remoteUpdatedAt)
        if (!referencesUnresolved && !localIsNewerOrEqual) {
            val entity =
                FeedbackEntity(
                    id = existing?.id ?: 0,
                    targetType = wire.targetType,
                    targetGarmentId = targetGarmentId,
                    targetOutfitId = targetOutfitId,
                    vote = wire.vote,
                    reasonCode = wire.reasonCode,
                    reasonText = wire.reasonText,
                    generatedStyleRuleId = generatedStyleRuleId,
                    createdAt = wire.createdAt,
                    updatedAt = remoteUpdatedAt,
                    syncId = syncId,
                )
            if (existing == null) feedbackDao.insert(entity) else feedbackDao.update(entity)
            return ApplyOutcome.Applied
        }
        return ApplyOutcome.LocalNewerIgnored
    }

    override suspend fun applyDelete(
        syncId: String,
        remoteDeletedAt: Long,
    ): ApplyOutcome {
        val existing = feedbackDao.getBySyncId(syncId)
        return when {
            existing == null -> {
                ApplyOutcome.Applied
            }

            existing.updatedAt > remoteDeletedAt -> {
                ApplyOutcome.EditDeleteConflict(
                    localSummary = "Edited on this device: feedback recorded ${existing.createdAt}",
                    remoteSummary = "Deleted on the other device",
                )
            }

            else -> {
                feedbackDao.deleteById(existing.id)
                ApplyOutcome.Applied
            }
        }
    }
}
