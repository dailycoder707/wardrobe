package com.wardrobe.app.core.model.ai

import java.time.Instant

/**
 * One row per [AiCapability] — the non-secret half of that capability's
 * configuration (ADR-012). The API key itself never lives here or in
 * `AiProviderPreferencesDataStore`'s backing store — only in
 * `core:ai`'s `EncryptedApiKeyStore`, keyed by the same [capability].
 *
 * [consentGrantedAt]/[consentHost] record the plain-language consent the
 * Settings screen requires before [mode] can be [AiProviderMode.CLOUD] —
 * changing [baseUrl] to a different host invalidates prior consent (the
 * router/Settings layer is responsible for clearing these two fields when
 * that happens, not this data class).
 */
data class AiProviderConfig(
    val capability: AiCapability,
    val mode: AiProviderMode,
    val vendor: AiVendor?,
    val baseUrl: String?,
    val model: String?,
    val costRatePerThousandTokens: Double?,
    val consentGrantedAt: Instant?,
    val consentHost: String?,
) {
    /** True only when every field a cloud dispatch actually needs is
     * present *and* consent was granted for the host currently configured —
     * changing the Base URL without re-consenting must not silently reuse
     * stale consent for a different destination. */
    fun isCloudReady(): Boolean =
        mode == AiProviderMode.CLOUD &&
            vendor != null &&
            !baseUrl.isNullOrBlank() &&
            consentGrantedAt != null &&
            consentHost == baseUrl

    companion object {
        fun onDeviceDefault(capability: AiCapability): AiProviderConfig =
            AiProviderConfig(
                capability = capability,
                mode = AiProviderMode.ON_DEVICE,
                vendor = null,
                baseUrl = null,
                model = null,
                costRatePerThousandTokens = null,
                consentGrantedAt = null,
                consentHost = null,
            )
    }
}
