package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiConnectionTestResult
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiUsageSummary
import kotlinx.coroutines.flow.Flow

/**
 * The Settings screen's single seam onto Add-to-Wardrobe v2's provider
 * architecture (ADR-012) — `feature:settings` depends only on `core:domain`
 * (this project's standing "features never depend on `core:ai`/
 * `core:datastore` directly" rule), so this interface is the only thing the
 * `AiProvidersScreen` ever sees; `core:data`'s implementation is the one
 * place that reaches into `core:ai`'s [com.wardrobe.app.core.ai.security.ApiKeyStore]/
 * `AiGateway` and `core:datastore`'s `AiProviderPreferencesDataStore`
 * together.
 *
 * The API key itself is never exposed by this interface — only whether one
 * is currently set ([hasApiKey]) — so the Settings screen can show
 * "configured"/"not configured" without ever holding the secret in
 * Compose state.
 */
interface AiProviderSettingsRepository {
    fun observeConfig(capability: AiCapability): Flow<AiProviderConfig>

    fun observeAllConfigs(): Flow<List<AiProviderConfig>>

    suspend fun setConfig(config: AiProviderConfig)

    suspend fun hasApiKey(capability: AiCapability): Boolean

    suspend fun setApiKey(
        capability: AiCapability,
        apiKey: String,
    )

    suspend fun clearApiKey(capability: AiCapability)

    /** Always a real, live dispatch through the configured vendor — never a
     * claimed-working state inferred from the config alone. */
    suspend fun testConnection(capability: AiCapability): AiConnectionTestResult

    fun observeUsageSummaries(): Flow<List<AiUsageSummary>>

    /** M15 Part 5's "Recent AI Activity" — a chronological (newest-first)
     * slice of the same `ai_call_log` [observeUsageSummaries] aggregates,
     * for a Home-facing feed rather than a per-capability rollup. */
    fun observeRecentActivity(limit: Int = DEFAULT_RECENT_ACTIVITY_LIMIT): Flow<List<AiActivityEntry>>

    /** M18 — every not-yet-terminal row (`PENDING`/`RUNNING`) in
     * `AiJobManager`'s own job ledger, newest-started first. This is the
     * real "is AI currently doing something" signal: empty whenever nothing
     * is genuinely in flight, never a fabricated "thinking" state. */
    fun observeActiveOperations(): Flow<List<AiActiveOperation>>

    companion object {
        const val DEFAULT_RECENT_ACTIVITY_LIMIT = 5
    }
}
