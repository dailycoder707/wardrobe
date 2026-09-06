package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.job.AiJobManager
import com.wardrobe.app.core.ai.metrics.AiMetrics
import com.wardrobe.app.core.ai.privacy.PrivacyPreprocessor
import com.wardrobe.app.core.database.dao.AiResultCacheDao
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiFallbackReasons
import com.wardrobe.app.core.model.ai.AiVendor
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only [AiGateway] implementation — see that interface's KDoc for the
 * dispatch order every call follows. `visionPromptAdapters`/
 * `imageTaskAdapters` are Hilt multibindings (`@Multibinds`, kept legally
 * empty until M6 adds real `@IntoMap` vendor adapters) so this class never
 * needs to change shape when a new vendor is added. Cache/telemetry
 * plumbing lives in sibling files (`GatewayCache.kt`, `DispatchOutcome.kt`)
 * to keep this class focused on the dispatch flow itself.
 */
@Singleton
class DefaultAiGateway
    @Inject
    constructor(
        private val privacyPreprocessor: PrivacyPreprocessor,
        private val aiResultCacheDao: AiResultCacheDao,
        private val imageCacheStore: AiResultImageCacheStore,
        private val aiJobManager: AiJobManager,
        private val aiMetrics: AiMetrics,
        private val visionPromptAdapters:
            Map<@JvmSuppressWildcards AiVendor, @JvmSuppressWildcards VisionPromptAdapter>,
        private val imageTaskAdapters: Map<@JvmSuppressWildcards AiVendor, @JvmSuppressWildcards ImageTaskAdapter>,
        private val clock: Clock,
    ) : AiGateway {
        override suspend fun runVisionPrompt(
            context: AiDispatchContext,
            promptVersion: String,
            systemPrompt: String,
            userPrompt: String,
            image: Bitmap,
            expectJsonResponse: Boolean,
        ): VisionPromptResult {
            val startedAt = clock.millis()
            val payload = preprocessPayload(context.capability, image)
            val cacheKey = cacheKeyFor(context, payload, promptVersion)
            val cached = cachedText(aiResultCacheDao, cacheKey)
            if (cached != null) {
                recordCacheHit(aiMetrics, context)
                val provenance = provenanceFor(context, promptVersion, clock, true, clock.millis() - startedAt)
                return VisionPromptResult.Success(cached, provenance)
            }
            val adapter = context.config.vendor?.let(visionPromptAdapters::get)
            val baseUrl = context.config.baseUrl
            return if (adapter == null || baseUrl.isNullOrBlank()) {
                recordMetric(aiMetrics, context, DispatchOutcome(0, AiCallOutcome.FAILURE))
                VisionPromptResult.Failure("vision_prompt_adapter_unavailable")
            } else {
                val model = context.config.model
                val request =
                    VisionPromptAdapterRequest(
                        baseUrl,
                        context.apiKey,
                        model,
                        systemPrompt,
                        userPrompt,
                        payload,
                        expectJsonResponse,
                    )
                dispatchVisionPrompt(context, promptVersion, cacheKey, adapter, request)
            }
        }

        override suspend fun runImageTask(
            context: AiDispatchContext,
            promptVersion: String,
            taskType: String,
            images: List<Bitmap>,
        ): ImageTaskResult {
            val startedAt = clock.millis()
            val payloads = images.map { preprocessPayload(context.capability, it) }
            val primaryHash = combinedImageHash(payloads)
            val cacheKey =
                buildCacheKey(
                    primaryHash,
                    context.capability,
                    context.config.vendor,
                    context.config.model,
                    promptVersion,
                )
            val cached = cachedImage(aiResultCacheDao, imageCacheStore, cacheKey)
            if (cached != null) {
                recordCacheHit(aiMetrics, context)
                return ImageTaskResult.Success(
                    cached.first,
                    cached.second,
                    provenanceFor(context, promptVersion, clock, true, clock.millis() - startedAt),
                )
            }
            val adapter = context.config.vendor?.let(imageTaskAdapters::get)
            val baseUrl = context.config.baseUrl
            return if (adapter == null || baseUrl.isNullOrBlank()) {
                recordMetric(aiMetrics, context, DispatchOutcome(0, AiCallOutcome.FAILURE))
                ImageTaskResult.Failure(AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE)
            } else {
                val request = ImageTaskAdapterRequest(baseUrl, context.apiKey, context.config.model, taskType, payloads)
                dispatchImageTask(context, promptVersion, cacheKey, adapter, request)
            }
        }

        private fun cacheKeyFor(
            context: AiDispatchContext,
            payload: Bitmap,
            promptVersion: String,
        ): String =
            buildCacheKey(
                sha256(payload),
                context.capability,
                context.config.vendor,
                context.config.model,
                promptVersion,
            )

        private suspend fun dispatchVisionPrompt(
            context: AiDispatchContext,
            promptVersion: String,
            cacheKey: String,
            adapter: VisionPromptAdapter,
            request: VisionPromptAdapterRequest,
        ): VisionPromptResult {
            val startedAt = clock.millis()
            return try {
                val dispatched = aiJobManager.dispatch(context.capability, cacheKey) { adapter.run(request) }
                handleVisionOutcome(context, promptVersion, cacheKey, dispatched, clock.millis() - startedAt)
            } catch (error: IOException) {
                dispatchFailure(context, clock.millis() - startedAt, error) { VisionPromptResult.Failure(it) }
            } catch (error: TimeoutCancellationException) {
                dispatchFailure(context, clock.millis() - startedAt, error) { VisionPromptResult.Failure(it) }
            }
        }

        private suspend fun dispatchImageTask(
            context: AiDispatchContext,
            promptVersion: String,
            cacheKey: String,
            adapter: ImageTaskAdapter,
            request: ImageTaskAdapterRequest,
        ): ImageTaskResult {
            val startedAt = clock.millis()
            return try {
                val dispatched = aiJobManager.dispatch(context.capability, cacheKey) { adapter.run(request) }
                handleImageTaskOutcome(context, promptVersion, cacheKey, dispatched, clock.millis() - startedAt)
            } catch (error: IOException) {
                dispatchFailure(context, clock.millis() - startedAt, error) { ImageTaskResult.Failure(it) }
            } catch (error: TimeoutCancellationException) {
                dispatchFailure(context, clock.millis() - startedAt, error) { ImageTaskResult.Failure(it) }
            }
        }

        private suspend fun <R> dispatchFailure(
            context: AiDispatchContext,
            latencyMs: Long,
            error: Throwable,
            toResult: (String) -> R,
        ): R {
            recordMetric(aiMetrics, context, DispatchOutcome(latencyMs, AiCallOutcome.TIMEOUT))
            return toResult(error.message ?: "dispatch_failed")
        }

        /** [dispatched]'s `isOwner` gates every side effect below (the cache
         * write, the metric event) — a caller that joined an in-flight
         * request for the identical cache key ([AiJobManager]'s KDoc) still
         * gets the same [VisionPromptResult] back, but must not redundantly
         * persist a cache row or metric event for a network call that only
         * actually happened once, from the owner's side. */
        private suspend fun handleVisionOutcome(
            context: AiDispatchContext,
            promptVersion: String,
            cacheKey: String,
            dispatched: AiJobManager.Dispatched<VisionPromptAdapterResult>,
            latencyMs: Long,
        ): VisionPromptResult =
            when (val outcome = dispatched.value) {
                is VisionPromptAdapterResult.Success -> {
                    if (dispatched.isOwner) {
                        val entity =
                            buildCacheEntity(cacheKey, context, promptVersion, outcome.rawResponseText, null, clock)
                        aiResultCacheDao.upsert(entity)
                        val dispatchOutcome =
                            DispatchOutcome(
                                latencyMs = latencyMs,
                                outcome = AiCallOutcome.SUCCESS,
                                estimatedInputTokens = outcome.estimatedInputTokens,
                                estimatedOutputTokens = outcome.estimatedOutputTokens,
                            )
                        recordMetric(aiMetrics, context, dispatchOutcome)
                    }
                    val provenance = provenanceFor(context, promptVersion, clock, false, latencyMs)
                    VisionPromptResult.Success(outcome.rawResponseText, provenance)
                }

                is VisionPromptAdapterResult.Failure -> {
                    if (dispatched.isOwner) {
                        recordMetric(aiMetrics, context, DispatchOutcome(latencyMs, AiCallOutcome.FAILURE))
                    }
                    VisionPromptResult.Failure(outcome.reason)
                }
            }

        /** Same owner-only side-effect gating as [handleVisionOutcome] — see
         * its KDoc. */
        private suspend fun handleImageTaskOutcome(
            context: AiDispatchContext,
            promptVersion: String,
            cacheKey: String,
            dispatched: AiJobManager.Dispatched<ImageTaskAdapterResult>,
            latencyMs: Long,
        ): ImageTaskResult =
            when (val outcome = dispatched.value) {
                is ImageTaskAdapterResult.Success -> {
                    if (dispatched.isOwner) {
                        val filePath = imageCacheStore.save(outcome.resultImage, sanitizeForFileName(cacheKey))
                        val entity =
                            buildCacheEntity(cacheKey, context, promptVersion, filePath, outcome.confidence, clock)
                        aiResultCacheDao.upsert(entity)
                        val dispatchOutcome = DispatchOutcome(latencyMs, AiCallOutcome.SUCCESS, outcome.confidence)
                        recordMetric(aiMetrics, context, dispatchOutcome)
                    }
                    ImageTaskResult.Success(
                        outcome.resultImage,
                        outcome.confidence,
                        provenanceFor(context, promptVersion, clock, false, latencyMs),
                    )
                }

                is ImageTaskAdapterResult.Failure -> {
                    if (dispatched.isOwner) {
                        recordMetric(aiMetrics, context, DispatchOutcome(latencyMs, AiCallOutcome.FAILURE))
                    }
                    ImageTaskResult.Failure(outcome.reason)
                }
            }

        private suspend fun preprocessPayload(
            capability: AiCapability,
            image: Bitmap,
        ): Bitmap =
            if (capability == AiCapability.GARMENT_EXTRACTION) {
                privacyPreprocessor.prepareExtractionPayload(image)
            } else {
                privacyPreprocessor.prepareGarmentPayload(image)
            }
    }

/**
 * [runImageTask]'s cache key must vary with every image that actually
 * shapes the result, not just the first — a single-image capability
 * (Extraction/Reconstruction) keeps its exact prior behavior (this
 * single-element branch is identical to the old `payloads.firstOrNull()?.
 * let(::sha256)`, so no existing cache entry's key changes), but Try-On's
 * body+garment(+mask) request would otherwise collide on body photo alone —
 * two different garments tried on the same body photo would incorrectly
 * share one cache entry. Combining every payload's own hash into one final
 * digest fixes that for any current or future multi-image capability
 * without changing the cache key's shape or this class's dispatch flow.
 */
private fun combinedImageHash(payloads: List<Bitmap>): String {
    if (payloads.size <= 1) return payloads.firstOrNull()?.let(::sha256).orEmpty()
    val digest = MessageDigest.getInstance("SHA-256")
    payloads.forEach { digest.update(sha256(it).toByteArray()) }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
