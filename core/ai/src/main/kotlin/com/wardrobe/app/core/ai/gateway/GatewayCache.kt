package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap
import com.wardrobe.app.core.database.dao.AiResultCacheDao
import com.wardrobe.app.core.database.entity.AiResultCacheEntity
import com.wardrobe.app.core.model.ai.AiResultSource
import java.time.Clock

internal suspend fun cachedText(
    dao: AiResultCacheDao,
    cacheKey: String,
): String? = dao.getByCacheKey(cacheKey)?.resultPayloadJson

internal suspend fun cachedImage(
    dao: AiResultCacheDao,
    store: AiResultImageCacheStore,
    cacheKey: String,
): Pair<Bitmap, Float?>? {
    val row = dao.getByCacheKey(cacheKey)
    val bitmap = row?.let { store.load(it.resultPayloadJson) }
    return if (row != null && bitmap != null) bitmap to row.confidence else null
}

internal fun buildCacheEntity(
    cacheKey: String,
    context: AiDispatchContext,
    promptVersion: String,
    payload: String,
    confidence: Float?,
    clock: Clock,
): AiResultCacheEntity =
    AiResultCacheEntity(
        cacheKey = cacheKey,
        capability = context.capability,
        provider = context.config.vendor?.name,
        model = context.config.model,
        promptVersion = promptVersion,
        source = AiResultSource.CLOUD,
        resultPayloadJson = payload,
        confidence = confidence,
        generatedAt = clock.millis(),
    )
