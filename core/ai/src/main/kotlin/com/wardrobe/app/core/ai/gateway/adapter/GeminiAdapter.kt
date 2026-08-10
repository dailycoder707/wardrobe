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

private const val DEFAULT_MODEL = "gemini-1.5-flash"

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
            val url = "${request.baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent"
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
                VisionPromptAdapterResult.Failure("http_error_${error.code()}")
            } catch (error: SerializationException) {
                VisionPromptAdapterResult.Failure("malformed_response: ${error.message}")
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
