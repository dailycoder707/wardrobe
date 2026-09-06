package com.wardrobe.app.core.data.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.image.segmentation.OnDeviceExtractionEngine
import com.wardrobe.app.core.image.segmentation.compositeGeminiSegmentationCutout
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiFallbackReasons
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.supportsCloudGarmentSegmentation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTRACTION_TASK_TYPE = "extract"
private const val EXTRACTION_DIAGNOSTICS_TAG = "MetadataPipeline"

/** Same debug-build gate as every other M22-M25 debug-only logging site —
 * checking the manifest's `FLAG_DEBUGGABLE` rather than adding a new
 * `BuildConfig` to a module that doesn't already have one. */
private fun isDebugBuild(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/**
 * The runtime router for `GARMENT_EXTRACTION` (ADR-012 §11) — the only
 * place anything above this layer needs to know cloud extraction exists at
 * all. On-device is the default and the automatic fallback whenever cloud
 * isn't fully configured/consented, or the dispatch itself fails — a
 * misconfigured or unreachable cloud provider degrades this capability, it
 * never breaks it.
 *
 * Two genuinely different cloud paths exist, chosen per-vendor via
 * [supportsCloudGarmentSegmentation] rather than a single generic call: a
 * vendor with an [com.wardrobe.app.core.ai.gateway.ImageTaskAdapter] binding
 * (only `GENERIC_REST` — a self-hosted image-in/image-out backend) uses
 * [AiGateway.runImageTask] unchanged since M22; Gemini, which instead
 * returns a real per-pixel segmentation mask as structured JSON from its
 * `VisionPromptAdapter` (M25 real-device follow-up — see
 * `GeminiSegmentationSupport`'s KDoc for the primary-source verification),
 * uses [AiGateway.runVisionPrompt] and composites the actual cutout itself
 * via `compositeGeminiSegmentationCutout`. Every other vendor (OpenAI,
 * Claude, …) has neither and dispatch-fails with
 * [AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE] from
 * `DefaultAiGateway.runImageTask`, same as before this change.
 *
 * Reuses the exact `MetadataPipeline` debug logcat tag `GarmentMetadataEngineRouter`
 * established (M23/M24) rather than inventing a second one — a developer
 * debugging on a real device wants one place to watch every capability's
 * dispatch, not one tag per capability.
 */
@Singleton
class GarmentExtractionEngineRouter
    @Inject
    constructor(
        private val onDeviceEngine: OnDeviceExtractionEngine,
        private val aiGateway: AiGateway,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
        @ApplicationContext private val context: Context,
    ) : GarmentExtractionEngine {
        override suspend fun extract(sourcePhoto: Bitmap): ExtractionResult {
            val config = preferencesDataStore.observeConfig(AiCapability.GARMENT_EXTRACTION).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.GARMENT_EXTRACTION)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) {
                logDiagnostics("On-device (Cloud AI not configured/consented for Garment Extraction)")
                return onDeviceEngine.extract(sourcePhoto)
            }

            val dispatchContext = AiDispatchContext(AiCapability.GARMENT_EXTRACTION, config, apiKey)
            return if (config.vendor?.supportsCloudGarmentSegmentation() == true) {
                extractViaGeminiSegmentation(dispatchContext, sourcePhoto, config)
            } else {
                extractViaImageTaskAdapter(dispatchContext, sourcePhoto, config)
            }
        }

        private suspend fun extractViaImageTaskAdapter(
            dispatchContext: AiDispatchContext,
            sourcePhoto: Bitmap,
            config: AiProviderConfig,
        ): ExtractionResult {
            val result =
                aiGateway.runImageTask(
                    dispatchContext,
                    PromptVersions.EXTRACTION_V1,
                    EXTRACTION_TASK_TYPE,
                    listOf(sourcePhoto),
                )
            return when (result) {
                is ImageTaskResult.Success -> {
                    logDiagnostics(cloudSuccessDiagnostics(config))
                    ExtractionResult.Success(result.resultImage, result.confidence, result.provenance)
                }

                is ImageTaskResult.Failure -> {
                    logDiagnostics(cloudFailureDiagnostics(config, result.reason))
                    // Cloud degrades this capability, it never breaks it —
                    // but a fallback result must never masquerade as the
                    // user's configured choice (M25 real-device finding,
                    // same principle as GarmentMetadataEngineRouter).
                    fallbackToOnDevice(sourcePhoto, result.reason)
                }
            }
        }

        /** Gemini has no [com.wardrobe.app.core.ai.gateway.ImageTaskAdapter]
         * binding — it dispatches through the same
         * [AiGateway.runVisionPrompt] path `GarmentMetadataEngineRouter`
         * already uses, since Gemini's segmentation output *is* structured
         * JSON, not a ready-made result image. A response that parses but
         * yields no usable detection/mask is a real, distinct outcome from a
         * dispatch failure — [AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE]
         * names it rather than folding it into a generic failure reason. */
        private suspend fun extractViaGeminiSegmentation(
            dispatchContext: AiDispatchContext,
            sourcePhoto: Bitmap,
            config: AiProviderConfig,
        ): ExtractionResult {
            val result =
                aiGateway.runVisionPrompt(
                    dispatchContext,
                    PromptVersions.EXTRACTION_GEMINI_SEGMENTATION_V1,
                    GEMINI_SEGMENTATION_SYSTEM_PROMPT,
                    GEMINI_SEGMENTATION_USER_PROMPT,
                    sourcePhoto,
                    expectJsonResponse = true,
                )
            return when (result) {
                is VisionPromptResult.Success -> {
                    val cutout =
                        parseGeminiSegmentationDetection(result.rawResponseText)
                            ?.let { compositeGeminiSegmentationCutout(sourcePhoto, it.box2d, requireNotNull(it.mask)) }
                    if (cutout == null) {
                        logDiagnostics(cloudFailureDiagnostics(config, AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE))
                        fallbackToOnDevice(sourcePhoto, AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE)
                    } else {
                        logDiagnostics(cloudSuccessDiagnostics(config))
                        ExtractionResult.Success(cutout.bitmap, cutout.confidence, result.provenance)
                    }
                }

                is VisionPromptResult.Failure -> {
                    logDiagnostics(cloudFailureDiagnostics(config, result.reason))
                    fallbackToOnDevice(sourcePhoto, result.reason)
                }
            }
        }

        private suspend fun fallbackToOnDevice(
            sourcePhoto: Bitmap,
            reason: String,
        ): ExtractionResult =
            when (val onDeviceResult = onDeviceEngine.extract(sourcePhoto)) {
                is ExtractionResult.Success -> onDeviceResult.asCloudFallback(reason)
                is ExtractionResult.Failure -> onDeviceResult
            }

        private fun logDiagnostics(message: String) {
            if (isDebugBuild(context)) Log.d(EXTRACTION_DIAGNOSTICS_TAG, message)
        }
    }

/** Marks an on-device extraction as the *consequence* of a failed cloud
 * attempt rather than a plain on-device run. Only the provenance changes —
 * the actual cutout pixels and confidence are exactly what the on-device
 * engine produced, never reinterpreted or faked. */
private fun ExtractionResult.Success.asCloudFallback(reason: String): ExtractionResult.Success =
    copy(provenance = provenance.copy(requestedSource = AiResultSource.CLOUD, fallbackReason = reason))

/** M25 real-device finding, updated by the M25 Gemini-segmentation
 * follow-up: `AdapterBindsModule` only binds `GENERIC_REST` to an
 * `ImageTaskAdapter` — there is no industry-standard image-in/image-out
 * wire shape a named vendor's chat-vision API can satisfy the way
 * `VisionPromptAdapter` covers metadata/styling. Gemini is no longer in
 * that bucket (see `GarmentExtractionEngineRouter.extractViaGeminiSegmentation`
 * above), but every *other* vendor still is: a `GARMENT_EXTRACTION` row
 * configured for OpenAI, Claude, etc. will still dispatch-fail with
 * [AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE] from
 * `DefaultAiGateway.runImageTask` — architectural, not transient, and this
 * diagnostic line exists so that's visible on a real device instead of
 * looking identical to a genuine network failure. The same reason string is
 * what `GarmentReviewMetadataScreen`'s fallback banner special-cases to
 * show a capability-absence message instead of a raw technical reason. */
private fun cloudSuccessDiagnostics(config: AiProviderConfig): String =
    "Cloud dispatch — provider=${config.vendor} model=${config.model ?: "default"} capability=GARMENT_EXTRACTION"

private fun cloudFailureDiagnostics(
    config: AiProviderConfig,
    reason: String,
): String {
    val note =
        if (reason == AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE) {
            " (this vendor has no image-segmentation adapter)"
        } else {
            ""
        }
    return "Cloud dispatch FAILED — provider=${config.vendor} model=${config.model ?: "default"} " +
        "capability=GARMENT_EXTRACTION reason=$reason$note — falling back to on-device"
}
