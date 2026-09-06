package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Only used when Settings has no model configured — the UI persists a real
 * one, so this is a floor, not the normal path.
 *
 * Deliberately an alias rather than a pinned version. Real-device testing
 * against a live key showed `generateContent` rejecting pinned names with
 * `404 … is no longer available to new users` (observed for both
 * `gemini-2.5-flash` and `gemini-2.5-flash-lite`), so *any* pinned default
 * has a shelf life and none can be right for every key — Gemini model
 * availability is a per-key entitlement. `gemini-flash-latest` is Google's
 * maintained "current flash" alias, which is the only kind of name that
 * doesn't age out. It is not verified against every key, and cannot be:
 * that is precisely why a failure here resolves to the model list
 * [GeminiAdapter.withAvailableModels] fetches for the user's own key.
 */
private const val DEFAULT_MODEL = "gemini-flash-latest"

private const val API_VERSION = "v1beta"
private const val GENERATE_CONTENT_METHOD = "generateContent"

/** Gemini's real documented `generateContent` endpoint —
 * `POST {baseUrl}/v1beta/models/{model}:generateContent`. Gemini's API
 * accepts the key either as a `?key=` query parameter or an
 * `x-goog-api-key` header; this deliberately uses the header, not the query
 * parameter — every `OkHttpClient` in this app (`AiNetworkModule`) runs an
 * `HttpLoggingInterceptor` at `Level.BASIC`, which logs the full request
 * URL (but never headers or the body). A key in the URL would be written
 * to Logcat on every real call; a key in a header never is. */
@Singleton
class GeminiAdapter
    @Inject
    constructor(
        private val service: GeminiService,
    ) : VisionPromptAdapter {
        override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
            val model = request.model ?: DEFAULT_MODEL
            val url = "${request.baseUrl.trimEnd('/')}/$API_VERSION/models/$model:$GENERATE_CONTENT_METHOD"
            val body =
                buildJsonObject {
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") { add(buildJsonObject { put("text", request.systemPrompt) }) }
                    }
                    putJsonArray("contents") {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                putJsonArray("parts") {
                                    add(buildJsonObject { put("text", request.userPrompt) })
                                    add(
                                        buildJsonObject {
                                            putJsonObject("inline_data") {
                                                put("mime_type", "image/webp")
                                                put("data", request.image.toBase64Webp())
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                    // M24 — Gemini's real, documented structured-output mode; see
                    // OpenAiCompatibleAdapterSupport's response_format KDoc for the
                    // same rationale (prompt already asks for JSON-only, this makes
                    // the provider itself enforce it rather than relying on
                    // compliance alone).
                    if (request.expectJsonResponse) {
                        putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
                    }
                }
            return try {
                toAdapterResult(service.generateContent(url, request.apiKey, body))
            } catch (error: HttpException) {
                // Gemini reports several unrelated conditions as a bare 404
                // (unreachable route vs. a model this key can't call); the
                // envelope's message is the only thing that separates them.
                VisionPromptAdapterResult.Failure(failureReason(error, request))
            } catch (error: SerializationException) {
                VisionPromptAdapterResult.Failure("malformed_response: ${error.message}")
            }
        }

        private suspend fun failureReason(
            error: HttpException,
            request: VisionPromptAdapterRequest,
        ): String {
            val reason = geminiFailureReason(error, request.apiKey)
            return if (reason.startsWith(MODEL_NOT_FOUND_LABEL)) withAvailableModels(reason, request) else reason
        }

        /**
         * A "model not found" is the one failure this app can make
         * actionable: show the user real candidate names instead of a dead
         * end, without ever guessing a replacement on their behalf.
         *
         * The wording is careful for a reason found on a real device:
         * `ListModels` returned `gemini-2.5-flash-lite` as supporting
         * `generateContent` while `generateContent` itself rejected that
         * exact model for the same key. So this list is what the API
         * *lists*, which is a strictly wider set than what the key may
         * actually call — presenting it as "available to you" would be
         * stating something the response does not establish.
         *
         * Strictly best-effort: if the lookup fails, the original reason
         * stands rather than being replaced by a second, less relevant one.
         */
        private suspend fun withAvailableModels(
            reason: String,
            request: VisionPromptAdapterRequest,
        ): String {
            val url = "${request.baseUrl.trimEnd('/')}/$API_VERSION/models"
            val listed =
                runCatching { service.listModels(url, request.apiKey) }
                    .getOrNull()
                    ?.models
                    .orEmpty()
                    .filter { GENERATE_CONTENT_METHOD in it.supportedGenerationMethods }
                    .mapNotNull { it.name?.substringAfter("models/") }
            return if (listed.isEmpty()) {
                reason
            } else {
                "$reason Models this API lists for generateContent (not all are callable by every key, " +
                    "so try one of these): ${listed.joinToString()}"
            }
        }

        private fun toAdapterResult(response: GeminiGenerateContentResponse): VisionPromptAdapterResult {
            val text =
                response.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
            return if (text == null) {
                VisionPromptAdapterResult.Failure("no_content_in_response")
            } else {
                VisionPromptAdapterResult.Success(
                    rawResponseText = text,
                    estimatedInputTokens = response.usageMetadata?.promptTokenCount,
                    estimatedOutputTokens = response.usageMetadata?.candidatesTokenCount,
                )
            }
        }
    }
