package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.reconstruction.OnDeviceReconstructionEngine
import com.wardrobe.app.core.image.reconstruction.ReconstructionResult
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.ConfidenceTier
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val RECONSTRUCTION_TASK_TYPE = "reconstruct"

/** The runtime router for `GARMENT_RECONSTRUCTION` (ADR-012 §11) — same
 * on-device-default/automatic-fallback shape as
 * [GarmentExtractionEngineRouter]. Only a configured cloud provider can
 * actually fill an occluded region; without one, this always resolves to
 * [OnDeviceReconstructionEngine]'s honest [ReconstructionResult.NotAttempted]. */
@Singleton
class GarmentReconstructionEngineRouter
    @Inject
    constructor(
        private val onDeviceEngine: OnDeviceReconstructionEngine,
        private val aiGateway: AiGateway,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
    ) : GarmentReconstructionEngine {
        override suspend fun reconstruct(
            cutout: Bitmap,
            extractionConfidence: Float?,
        ): ReconstructionResult {
            val config = preferencesDataStore.observeConfig(AiCapability.GARMENT_RECONSTRUCTION).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.GARMENT_RECONSTRUCTION)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) {
                return onDeviceEngine.reconstruct(cutout, extractionConfidence)
            }

            val context = AiDispatchContext(AiCapability.GARMENT_RECONSTRUCTION, config, apiKey)
            val result =
                aiGateway.runImageTask(
                    context,
                    PromptVersions.RECONSTRUCTION_V1,
                    RECONSTRUCTION_TASK_TYPE,
                    listOf(cutout),
                )
            return when (result) {
                // Only ever auto-apply a cloud fill when the provider's own
                // confidence is genuinely high (the user's explicit
                // requirement) — anything less falls back to the honest
                // on-device NotAttempted (which becomes a retake prompt),
                // never a guessed-but-uncertain fill.
                is ImageTaskResult.Success -> {
                    if (ConfidenceTier.forConfidence(result.confidence) == ConfidenceTier.HIGH) {
                        ReconstructionResult.Reconstructed(result.resultImage, result.confidence!!)
                    } else {
                        onDeviceEngine.reconstruct(cutout, extractionConfidence)
                    }
                }

                is ImageTaskResult.Failure -> {
                    onDeviceEngine.reconstruct(cutout, extractionConfidence)
                }
            }
        }
    }
