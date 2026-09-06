package com.wardrobe.app.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The non-secret half of each [AiCapability]'s provider configuration
 * (ADR-012) — mode, vendor, Base URL, model, optional cost-rate, and
 * consent state. The API key itself is never read or written here; see
 * `core:ai`'s `EncryptedApiKeyStore`.
 */
@Singleton
class AiProviderPreferencesDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        fun observeConfig(capability: AiCapability): Flow<AiProviderConfig> =
            dataStore.data.map { prefs -> prefs.toConfig(capability) }

        fun observeAllConfigs(): Flow<List<AiProviderConfig>> =
            dataStore.data.map { prefs -> AiCapability.entries.map { prefs.toConfig(it) } }

        suspend fun setConfig(config: AiProviderConfig) {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.aiProviderMode(config.capability)] = config.mode.name
                prefs.putOrRemove(PreferenceKeys.aiProviderVendor(config.capability), config.vendor?.name)
                prefs.putOrRemove(PreferenceKeys.aiProviderBaseUrl(config.capability), config.baseUrl)
                prefs.putOrRemove(PreferenceKeys.aiProviderModel(config.capability), config.model)
                prefs.putOrRemove(PreferenceKeys.aiProviderConsentHost(config.capability), config.consentHost)
                val costRate = config.costRatePerThousandTokens
                if (costRate != null) {
                    prefs[PreferenceKeys.aiProviderCostRate(config.capability)] = costRate
                } else {
                    prefs.remove(PreferenceKeys.aiProviderCostRate(config.capability))
                }
                val consentGrantedAt = config.consentGrantedAt
                val consentGrantedAtKey = PreferenceKeys.aiProviderConsentGrantedAt(config.capability)
                if (consentGrantedAt != null) {
                    prefs[consentGrantedAtKey] = consentGrantedAt.toEpochMilli()
                } else {
                    prefs.remove(consentGrantedAtKey)
                }
            }
        }

        private fun MutablePreferences.putOrRemove(
            key: Preferences.Key<String>,
            value: String?,
        ) {
            if (value != null) this[key] = value else remove(key)
        }

        private fun Preferences.toConfig(capability: AiCapability): AiProviderConfig {
            val mode =
                this[PreferenceKeys.aiProviderMode(capability)]
                    ?.let { runCatching { AiProviderMode.valueOf(it) }.getOrNull() }
                    ?: AiProviderMode.ON_DEVICE
            val vendor =
                this[PreferenceKeys.aiProviderVendor(capability)]
                    ?.let { runCatching { AiVendor.valueOf(it) }.getOrNull() }
            return AiProviderConfig(
                capability = capability,
                mode = mode,
                vendor = vendor,
                baseUrl = this[PreferenceKeys.aiProviderBaseUrl(capability)],
                model = this[PreferenceKeys.aiProviderModel(capability)],
                costRatePerThousandTokens = this[PreferenceKeys.aiProviderCostRate(capability)],
                consentGrantedAt =
                    this[PreferenceKeys.aiProviderConsentGrantedAt(capability)]?.let(Instant::ofEpochMilli),
                consentHost = this[PreferenceKeys.aiProviderConsentHost(capability)],
            )
        }
    }
