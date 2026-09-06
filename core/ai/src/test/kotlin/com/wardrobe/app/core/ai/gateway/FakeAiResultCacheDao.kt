package com.wardrobe.app.core.ai.gateway

import com.wardrobe.app.core.database.dao.AiResultCacheDao
import com.wardrobe.app.core.database.entity.AiResultCacheEntity

class FakeAiResultCacheDao : AiResultCacheDao {
    private val rows = mutableMapOf<String, AiResultCacheEntity>()
    var upsertCallCount: Int = 0
        private set

    override suspend fun upsert(entity: AiResultCacheEntity): Long {
        upsertCallCount++
        rows[entity.cacheKey] = entity
        return 1L
    }

    override suspend fun getByCacheKey(cacheKey: String): AiResultCacheEntity? = rows[cacheKey]

    override suspend fun deleteByCacheKey(cacheKey: String) {
        rows.remove(cacheKey)
    }

    override suspend fun deleteOlderThan(olderThanEpochMillis: Long) {
        rows.values.filter { it.generatedAt < olderThanEpochMillis }.forEach { rows.remove(it.cacheKey) }
    }
}
