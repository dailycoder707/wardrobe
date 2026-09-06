package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException

/** Shared by every OpenAI-compatible adapter (OpenAI, Azure OpenAI,
 * OpenRouter, Ollama) — only the URL and auth header differ per vendor,
 * built by each adapter's own small class. */
internal suspend fun runChatCompletions(
    service: OpenAiCompatibleService,
    url: String,
    headers: Map<String, String>,
    request: VisionPromptAdapterRequest,
): VisionPromptAdapterResult {
    val body = buildChatCompletionsRequestBody(request)
    return try {
        val response = service.chatCompletions(url, headers, body)
        toAdapterResult(response)
    } catch (error: HttpException) {
        VisionPromptAdapterResult.Failure("http_error_${error.code()}")
    } catch (error: SerializationException) {
        VisionPromptAdapterResult.Failure("malformed_response: ${error.message}")
    }
}

/** M24 — when the caller's prompt expects JSON-only output, request
 * OpenAI's `json_object` mode instead of relying purely on the system
 * prompt's own "respond with ONLY a JSON object" instruction. Shared by
 * every OpenAI-compatible vendor (OpenAI, Azure OpenAI, OpenRouter,
 * Ollama's compatibility endpoint) via [runChatCompletions]. A backend that
 * doesn't understand this field (an older/incompatible self-hosted model
 * behind Ollama or OpenRouter) fails the HTTP call, which the existing
 * `catch (error: HttpException)` below already turns into an honest
 * [VisionPromptAdapterResult.Failure] — the same fallback-to-on-device path
 * already tested, never a crash. */
private fun buildChatCompletionsRequestBody(request: VisionPromptAdapterRequest): JsonObject =
    buildJsonObject {
        request.model?.let { put("model", it) }
        if (request.expectJsonResponse) {
            putJsonObject("response_format") { put("type", "json_object") }
        }
        putJsonArray("messages") {
            addObject {
                put("role", "system")
                put("content", request.systemPrompt)
            }
            addObject {
                put("role", "user")
                putJsonArray("content") {
                    addObject {
                        put("type", "text")
                        put("text", request.userPrompt)
                    }
                    addObject {
                        put("type", "image_url")
                        putJsonObject(
                            "image_url",
                        ) { put("url", "data:image/webp;base64,${request.image.toBase64Webp()}") }
                    }
                }
            }
        }
    }

private fun JsonArrayBuilder.addObject(builderAction: JsonObjectBuilder.() -> Unit) {
    add(buildJsonObject(builderAction))
}

private fun toAdapterResult(response: ChatCompletionsResponse): VisionPromptAdapterResult {
    val text =
        response.choices
            .firstOrNull()
            ?.message
            ?.content
    return if (text == null) {
        VisionPromptAdapterResult.Failure("no_content_in_response")
    } else {
        VisionPromptAdapterResult.Success(
            rawResponseText = text,
            estimatedInputTokens = response.usage?.promptTokens,
            estimatedOutputTokens = response.usage?.completionTokens,
        )
    }
}
