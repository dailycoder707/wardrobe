package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.data.mapper.toActiveOperations
import com.wardrobe.app.core.data.mapper.toActivityEntry
import com.wardrobe.app.core.data.mapper.toUsageSummaries
import com.wardrobe.app.core.database.dao.AiCallLogDao
import com.wardrobe.app.core.database.dao.AiJobDao
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiUsageSummary
import com.wardrobe.app.core.model.ai.AiVendor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Settings screen's only seam onto the provider architecture (ADR-012) —
 * see [AiProviderSettingsRepository]'s KDoc for why this is the one class
 * that reaches into `core:ai`'s [ApiKeyStore]/[AiGateway] and
 * `core:datastore`'s [AiProviderPreferencesDataStore] together.
 */
@Singleton
class AiProviderSettingsRepositoryImpl
    @Inject
    constructor(
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
        private val aiGateway: AiGateway,
        private val aiCallLogDao: AiCallLogDao,
        private val aiJobDao: AiJobDao,
        private val clock: Clock,
    ) : AiProviderSettingsRepository {
        override fun observeConfig(capability: AiCapability): Flow<AiProviderConfig> =
            preferencesDataStore.observeConfig(capability)

        override fun observeAllConfigs(): Flow<List<AiProviderConfig>> = preferencesDataStore.observeAllConfigs()

        override suspend fun setConfig(config: AiProviderConfig) = preferencesDataStore.setConfig(config)

        override suspend fun hasApiKey(capability: AiCapability): Boolean =
            !apiKeyStore.getApiKey(capability).isNullOrBlank()

        override suspend fun setApiKey(
            capability: AiCapability,
            apiKey: String,
        ) = apiKeyStore.setApiKey(capability, apiKey)

        override suspend fun clearApiKey(capability: AiCapability) = apiKeyStore.setApiKey(capability, null)

        override suspend fun testConnection(capability: AiCapability): AiConnectionTestResult {
            val config = preferencesDataStore.observeConfig(capability).first()
            val apiKey = apiKeyStore.getApiKey(capability)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) {
                return AiConnectionTestResult.Failure("This capability isn't fully configured for cloud yet.")
            }
            val context = AiDispatchContext(capability, config, apiKey)
            val startedAt = clock.millis()
            return if (config.vendor == AiVendor.GENERIC_REST) {
                testImageTaskConnection(aiGateway, context, clock, startedAt)
            } else {
                testVisionPromptConnection(aiGateway, context, clock, startedAt)
            }
        }

        override fun observeUsageSummaries(): Flow<List<AiUsageSummary>> =
            aiCallLogDao.observeAll().map { entities -> entities.toUsageSummaries() }

        override fun observeRecentActivity(limit: Int): Flow<List<AiActivityEntry>> =
            aiCallLogDao.observeAll().map { entities -> entities.take(limit).map { it.toActivityEntry() } }

        override fun observeActiveOperations(): Flow<List<AiActiveOperation>> =
            aiJobDao.observeAll().map { entities -> entities.toActiveOperations() }
    }
