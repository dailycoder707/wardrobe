package com.wardrobe.app.core.ai.security

import com.wardrobe.app.core.model.ai.AiCapability

/**
 * Secure storage for cloud-provider API keys (ADR-012) — one key per
 * [AiCapability]. Deliberately an interface: the real implementation
 * ([EncryptedApiKeyStore]) needs `androidx.security.crypto`'s
 * `EncryptedSharedPreferences`, which isn't available under a plain
 * JVM/Robolectric test — tests substitute an in-memory fake instead, the
 * same seam pattern this project already uses for
 * `DeviceIdentityKeyStore`/`WeatherProvider`. Never stored in the plain
 * `AiProviderPreferencesDataStore` preferences file — only here.
 */
interface ApiKeyStore {
    fun getApiKey(capability: AiCapability): String?

    /** `null` or blank clears the stored key for this capability. */
    fun setApiKey(
        capability: AiCapability,
        apiKey: String?,
    )
}
