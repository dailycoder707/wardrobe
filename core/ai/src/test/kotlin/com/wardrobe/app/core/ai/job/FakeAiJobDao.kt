package com.wardrobe.app.core.ai.job

import com.wardrobe.app.core.database.dao.AiJobDao
import com.wardrobe.app.core.database.entity.AiJobEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAiJobDao : AiJobDao {
    private val rows = mutableMapOf<Long, AiJobEntity>()
    private val state = MutableStateFlow<List<AiJobEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: AiJobEntity): Long {
        val id = nextId++
        rows[id] = entity.copy(id = id)
        publish()
        return id
    }

    override suspend fun update(entity: AiJobEntity) {
        rows[entity.id] = entity
        publish()
    }

    override suspend fun getByCacheKey(cacheKey: String): AiJobEntity? =
        rows.values
            .filter {
                it.cacheKey == cacheKey
            }.maxByOrNull { it.createdAt }

    override fun observeAll() = state.asStateFlow()

    override suspend fun deleteFinished() {
        val toRemove = rows.filterValues { it.status.name in setOf("SUCCEEDED", "FAILED", "CANCELLED") }.keys
        toRemove.forEach { rows.remove(it) }
        publish()
    }

    private fun publish() {
        state.value = rows.values.sortedByDescending { it.createdAt }
    }
}
