package com.wardrobe.app.core.data.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.metadata.OnDeviceMetadataEngine
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val METADATA_DIAGNOSTICS_TAG = "MetadataPipeline"

/** M23/M24 — same debug-build gate as `core:ai`'s `AiNetworkModule`/
 * `feature:capture`'s `GarmentReviewMetadataViewModel` (checking the
 * manifest's `FLAG_DEBUGGABLE` rather than adding a new `BuildConfig` to a
 * module that doesn't already have one). */
private fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** The runtime router for `GARMENT_METADATA` (ADR-012 §11) — same
 * on-device-default/automatic-fallback shape as the extraction/
 * reconstruction routers. The cloud path's raw response text is parsed via
 * [parseMetadataSuggestions], which never fabricates a suggestion from a
 * malformed or partial response.
 *
 * M24's debug-only diagnostics (the request-level half; the per-field
 * funnel is logged separately by `GarmentReviewMetadataViewModel`, feature:
 * capture — see its own KDoc) never log the API key, an auth header, the
 * image, or the raw provider response text — only provider/model/capability
 * identifiers, the field *names* requested vs. returned, and the cache-hit/
 * failure-reason booleans/strings already used for the (non-secret) result. */
@Singleton
class GarmentMetadataEngineRouter
    @Inject
    constructor(
        private val onDeviceEngine: OnDeviceMetadataEngine,
        private val aiGateway: AiGateway,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
        @ApplicationContext private val context: Context,
    ) : GarmentMetadataEngine {
        override suspend fun generateMetadata(cutout: Bitmap): List<MetadataSuggestion> {
            val config = preferencesDataStore.observeConfig(AiCapability.GARMENT_METADATA).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.GARMENT_METADATA)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) {
                logDiagnostics("On-device (Cloud AI not configured/consented for Garment Metadata)")
                return onDeviceEngine.generateMetadata(cutout)
            }

            val dispatchContext = AiDispatchContext(AiCapability.GARMENT_METADATA, config, apiKey)
            val result =
                aiGateway.runVisionPrompt(
                    dispatchContext,
                    PromptVersions.METADATA_V2,
                    buildMetadataSystemPrompt(),
                    METADATA_USER_PROMPT,
                    cutout,
                    expectJsonResponse = true,
                )
            return when (result) {
                is VisionPromptResult.Success -> {
                    val suggestions = parseMetadataSuggestions(result.rawResponseText, result.provenance)
                    logDiagnostics(cloudSuccessDiagnostics(config, result.provenance.cacheHit, suggestions))
                    suggestions
                }

                is VisionPromptResult.Failure -> {
                    logDiagnostics(cloudFailureDiagnostics(config, result.reason))
                    onDeviceEngine.generateMetadata(cutout)
                }
            }
        }

        private fun logDiagnostics(message: String) {
            if (isDebugBuild(context)) Log.d(METADATA_DIAGNOSTICS_TAG, message)
        }
    }

private fun cloudSuccessDiagnostics(
    config: AiProviderConfig,
    cacheHit: Boolean,
    suggestions: List<MetadataSuggestion>,
): String {
    val requested = MetadataField.entries.joinToString(",")
    val returned = suggestions.map { it.field }.distinct().joinToString(",")
    return "Cloud dispatch — provider=${config.vendor} model=${config.model ?: "default"} " +
        "capability=GARMENT_METADATA cacheHit=$cacheHit requested=[$requested] returned=[$returned]"
}

private fun cloudFailureDiagnostics(
    config: AiProviderConfig,
    reason: String,
): String =
    "Cloud dispatch FAILED — provider=${config.vendor} model=${config.model ?: "default"} " +
        "capability=GARMENT_METADATA reason=$reason — falling back to on-device"
