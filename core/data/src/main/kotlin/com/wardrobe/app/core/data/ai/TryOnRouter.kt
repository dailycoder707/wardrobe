package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.ai.tryon.VirtualTryOnEngine
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.tryon.engine.OnDeviceVirtualTryOnEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime router for `VIRTUAL_TRY_ON` (M12) — the same on-device-
 * default/automatic-fallback shape as every other capability's Router
 * (ADR-012 §11). A cloud render that fails validation or dispatch falls
 * back to [onDeviceEngine] rather than surfacing nothing: cloud degrades
 * this capability, it never breaks it.
 */
@Singleton
class TryOnRouter
    @Inject
    constructor(
        private val onDeviceEngine: OnDeviceVirtualTryOnEngine,
        private val cloudTryOnEngine: CloudTryOnEngine,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
    ) : VirtualTryOnEngine {
        override suspend fun render(
            bodyPhoto: Bitmap,
            garmentCutout: Bitmap,
            mask: Bitmap?,
        ): TryOnRenderResult {
            val config = preferencesDataStore.observeConfig(AiCapability.VIRTUAL_TRY_ON).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.VIRTUAL_TRY_ON)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) {
                return onDeviceEngine.render(bodyPhoto, garmentCutout, mask)
            }
            return when (val cloud = cloudTryOnEngine.render(bodyPhoto, garmentCutout, mask, config, apiKey)) {
                is TryOnRenderResult.Success -> cloud
                is TryOnRenderResult.Failure -> onDeviceEngine.render(bodyPhoto, garmentCutout, mask)
            }
        }
    }
