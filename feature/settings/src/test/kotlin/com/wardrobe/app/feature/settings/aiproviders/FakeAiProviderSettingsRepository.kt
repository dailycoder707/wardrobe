package com.wardrobe.app.feature.settings.aiproviders

import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiUsageSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeAiProviderSettingsRepository : AiProviderSettingsRepository {
    val configsFlow =
        MutableStateFlow(AiCapability.entries.associateWith { AiProviderConfig.onDeviceDefault(it) })
    val apiKeys = mutableMapOf<AiCapability, String>()
    val usageFlow = MutableStateFlow<List<AiUsageSummary>>(emptyList())
    val activityFlow = MutableStateFlow<List<AiActivityEntry>>(emptyList())
    val activeOperationsFlow = MutableStateFlow<List<AiActiveOperation>>(emptyList())
    var nextTestConnectionResult: AiConnectionTestResult = AiConnectionTestResult.Success(42)
    var testConnectionCallCount = 0

    override fun observeConfig(capability: AiCapability): Flow<AiProviderConfig> =
        configsFlow.asStateFlow().map { it.getValue(capability) }

    override fun observeAllConfigs(): Flow<List<AiProviderConfig>> =
        configsFlow.asStateFlow().map { map -> AiCapability.entries.map { map.getValue(it) } }

    override suspend fun setConfig(config: AiProviderConfig) {
        configsFlow.value = configsFlow.value + (config.capability to config)
    }

    override suspend fun hasApiKey(capability: AiCapability): Boolean = !apiKeys[capability].isNullOrBlank()

    override suspend fun setApiKey(
        capability: AiCapability,
        apiKey: String,
    ) {
        apiKeys[capability] = apiKey
    }

    override suspend fun clearApiKey(capability: AiCapability) {
        apiKeys.remove(capability)
    }

    override suspend fun testConnection(capability: AiCapability): AiConnectionTestResult {
        testConnectionCallCount++
        return nextTestConnectionResult
    }

    override fun observeUsageSummaries(): Flow<List<AiUsageSummary>> = usageFlow.asStateFlow()

    override fun observeRecentActivity(limit: Int): Flow<List<AiActivityEntry>> =
        activityFlow.asStateFlow().map { it.take(limit) }

    override fun observeActiveOperations(): Flow<List<AiActiveOperation>> = activeOperationsFlow.asStateFlow()
}
