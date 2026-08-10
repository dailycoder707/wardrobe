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

private const val DEFAULT_MODEL = "claude-3-5-sonnet-20241022"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val MAX_TOKENS = 1024

/** Anthropic's real documented Messages API —
 * `POST {baseUrl}/v1/messages`, `x-api-key: <key>` +
 * `anthropic-version: 2023-06-01` headers, image content blocks distinct
 * from OpenAI's `image_url` shape (`{"type": "image", "source": {"type":
 * "base64", "media_type": ..., "data": ...}}`). */
@Singleton
class ClaudeAdapter
    @Inject
    constructor(
        private val service: ClaudeService,
    ) : VisionPromptAdapter {
        override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
            val url = "${request.baseUrl.trimEnd('/')}/v1/messages"
            val headers = mapOf("x-api-key" to request.apiKey, "anthropic-version" to ANTHROPIC_VERSION)
            val body =
                buildJsonObject {
                    put("model", request.model ?: DEFAULT_MODEL)
                    put("max_tokens", MAX_TOKENS)
                    put("system", request.systemPrompt)
                    putJsonArray("messages") {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                putJsonArray("content") {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", request.userPrompt)
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", "image/webp")
                                                put("data", request.image.toBase64Webp())
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            return try {
                toAdapterResult(service.createMessage(url, headers, body))
            } catch (error: HttpException) {
                VisionPromptAdapterResult.Failure("http_error_${error.code()}")
            } catch (error: SerializationException) {
                VisionPromptAdapterResult.Failure("malformed_response: ${error.message}")
            }
        }

        private fun toAdapterResult(response: ClaudeMessagesResponse): VisionPromptAdapterResult {
            val text = response.content.firstOrNull { it.type == "text" }?.text
            return if (text == null) {
                VisionPromptAdapterResult.Failure("no_content_in_response")
            } else {
                VisionPromptAdapterResult.Success(
                    rawResponseText = text,
                    estimatedInputTokens = response.usage?.inputTokens,
                    estimatedOutputTokens = response.usage?.outputTokens,
                )
            }
        }
    }
