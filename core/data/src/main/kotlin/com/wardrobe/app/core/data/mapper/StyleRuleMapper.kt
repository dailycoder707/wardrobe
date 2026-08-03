package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.FeedbackEntity
import com.wardrobe.app.core.database.entity.StyleRuleEntity
import com.wardrobe.app.core.model.common.FeedbackId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.StyleRuleId
import com.wardrobe.app.core.model.styling.Feedback
import com.wardrobe.app.core.model.styling.StyleRule
import java.time.Instant

internal fun StyleRuleEntity.toDomain() =
    StyleRule(
        id = StyleRuleId(id),
        description = description,
        sourceType = sourceType,
        sourceFeedbackId = sourceFeedbackId?.let(::FeedbackId),
        ruleType = ruleType,
        parametersJson = parametersJson,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

internal fun StyleRule.toEntity() =
    StyleRuleEntity(
        id = id.value,
        description = description,
        sourceType = sourceType,
        sourceFeedbackId = sourceFeedbackId?.value,
        ruleType = ruleType,
        parametersJson = parametersJson,
        isActive = isActive,
        createdAt = createdAt.toEpochMilli(),
    )

internal fun Feedback.toEntity() =
    FeedbackEntity(
        id = id.value,
        targetType = targetType,
        targetGarmentId = targetGarmentId?.value,
        targetOutfitId = targetOutfitId?.value,
        vote = vote,
        reasonCode = reasonCode,
        reasonText = reasonText,
        generatedStyleRuleId = generatedStyleRuleId?.value,
        createdAt = createdAt.toEpochMilli(),
    )

internal fun FeedbackEntity.toDomain() =
    Feedback(
        id = FeedbackId(id),
        targetType = targetType,
        targetGarmentId = targetGarmentId?.let(::GarmentId),
        targetOutfitId = targetOutfitId?.let(::OutfitId),
        vote = vote,
        reasonCode = reasonCode,
        reasonText = reasonText,
        generatedStyleRuleId = generatedStyleRuleId?.let(::StyleRuleId),
        createdAt = Instant.ofEpochMilli(createdAt),
    )
