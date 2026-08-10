package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.image.segmentation.OnDeviceExtractionEngine
import com.wardrobe.app.core.model.ai.AiCapability
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTRACTION_TASK_TYPE = "extract"

/**
 * The runtime router for `GARMENT_EXTRACTION` (ADR-012 §11) — the only
 * place anything above this layer needs to know cloud extraction exists at
 * all. On-device is the default and the automatic fallback whenever cloud
 * isn't fully configured/consented or the dispatch itself fails; a
 * misconfigured or unreachable cloud provider degrades this capability, it
 * never breaks it.
 */
@Singleton
class GarmentExtractionEngineRouter
    @Inject
    constructor(
        private val onDeviceEngine: OnDeviceExtractionEngine,
        private val aiGateway: AiGateway,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
    ) : GarmentExtractionEngine {
        override suspend fun extract(sourcePhoto: Bitmap): ExtractionResult {
            val config = preferencesDataStore.observeConfig(AiCapability.GARMENT_EXTRACTION).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.GARMENT_EXTRACTION)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) return onDeviceEngine.extract(sourcePhoto)

            val context = AiDispatchContext(AiCapability.GARMENT_EXTRACTION, config, apiKey)
            val result =
                aiGateway.runImageTask(context, PromptVersions.EXTRACTION_V1, EXTRACTION_TASK_TYPE, listOf(sourcePhoto))
            return when (result) {
                is ImageTaskResult.Success -> ExtractionResult.Success(result.resultImage, result.confidence)
                is ImageTaskResult.Failure -> onDeviceEngine.extract(sourcePhoto)
            }
        }
    }
