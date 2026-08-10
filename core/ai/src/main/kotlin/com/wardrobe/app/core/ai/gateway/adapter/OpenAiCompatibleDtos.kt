package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The chat-completions-with-vision wire shape OpenAI, Azure OpenAI,
 * OpenRouter, and Ollama's OpenAI-compatible endpoint all share — the
 * literal reason "OpenAI-compatible" is a meaningful, vendor-neutral
 * category (ADR-012). Every field the response doesn't actually contain
 * defaults to `null`/empty rather than failing to parse — an unexpected or
 * partial response is a [VisionPromptAdapterResult.Failure], never a crash.
 */
@Serializable
data class ChatCompletionsResponse(
    val choices: List<ChatCompletionsChoice> = emptyList(),
    val usage: ChatCompletionsUsage? = null,
)

@Serializable
data class ChatCompletionsChoice(
    val message: ChatCompletionsMessage? = null,
)

@Serializable
data class ChatCompletionsMessage(
    val content: String? = null,
)

@Serializable
data class ChatCompletionsUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)
