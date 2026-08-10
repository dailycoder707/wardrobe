package com.wardrobe.app.core.ai.security

import com.wardrobe.app.core.model.ai.AiCapability

/** In-memory stand-in for [EncryptedApiKeyStore] — `AndroidKeyStore`/
 * `EncryptedSharedPreferences` aren't available under a plain JVM test,
 * same reason `core:sync`'s `DeviceIdentityKeyStore` gets a plain fake. */
class FakeApiKeyStore : ApiKeyStore {
    private val keys = mutableMapOf<AiCapability, String>()

    override fun getApiKey(capability: AiCapability): String? = keys[capability]

    override fun setApiKey(
        capability: AiCapability,
        apiKey: String?,
    ) {
        if (apiKey.isNullOrBlank()) keys.remove(capability) else keys[capability] = apiKey
    }
}
