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
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.FabricRepository
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.metadata.OnDeviceMetadataEngine
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Bundles the six reference-data repositories the cloud metadata prompt
 * needs (M25 real-device finding — see [MetadataReferenceOptions]'s own
 * KDoc) purely to keep [GarmentMetadataEngineRouter]'s constructor under
 * this project's `LongParameterList` threshold, the same "bag of
 * repositories" pattern `feature:capture`'s `ReviewTaxonomyRepositories`
 * already uses. Deliberately excludes `BrandRepository` — brand is never
 * constrained to already-known values. */
class MetadataReferenceRepositories
    @Inject
    constructor(
        val categoryRepository: CategoryRepository,
        val colorRepository: ColorRepository,
        val materialRepository: MaterialRepository,
        val fabricRepository: FabricRepository,
        val occasionRepository: OccasionRepository,
        val tagRepository: TagRepository,
    )

private suspend fun MetadataReferenceRepositories.fetchOptions(): MetadataReferenceOptions =
    MetadataReferenceOptions(
        categoryNames = categoryRepository.observeAll().first().map { it.name },
        colorNames = colorRepository.observeAll().first().map { it.name },
        materialNames = materialRepository.observeAll().first().map { it.name },
        fabricNames = fabricRepository.observeAll().first().map { it.name },
        occasionNames = occasionRepository.observeAll().first().map { it.name },
        tagNames = tagRepository.observeAll().first().map { it.name },
    )

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
        private val referenceRepositories: MetadataReferenceRepositories,
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
            val referenceOptions = referenceRepositories.fetchOptions()
            val result =
                aiGateway.runVisionPrompt(
                    dispatchContext,
                    PromptVersions.METADATA_V3,
                    buildMetadataSystemPrompt(referenceOptions),
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
                    // The on-device result is still the right thing to
                    // return — cloud degrades a capability, it never breaks
                    // one — but it must not masquerade as the user's
                    // configured choice. Tagging it here is what lets the
                    // review screen say "Cloud unavailable, used On-Device".
                    onDeviceEngine.generateMetadata(cutout).map { it.asCloudFallback(result.reason) }
                }
            }
        }

        private fun logDiagnostics(message: String) {
            if (isDebugBuild(context)) Log.d(METADATA_DIAGNOSTICS_TAG, message)
        }
    }

/** Marks an on-device suggestion as the *consequence* of a failed cloud
 * attempt rather than a plain on-device run. Only the provenance changes —
 * the suggested value, its confidence and its field are exactly what the
 * on-device engine produced, so nothing about the suggestion itself is
 * fabricated or reinterpreted. */
private fun MetadataSuggestion.asCloudFallback(reason: String): MetadataSuggestion =
    copy(
        provenance =
            provenance.copy(
                requestedSource = AiResultSource.CLOUD,
                fallbackReason = reason,
            ),
    )

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
