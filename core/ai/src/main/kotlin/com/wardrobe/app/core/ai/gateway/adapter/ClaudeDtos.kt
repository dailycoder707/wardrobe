package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anthropic's real documented Messages API response shape — a top-level
 * `content` array of typed blocks and its own `usage` field names
 * (`input_tokens`/`output_tokens`, not `prompt_tokens`/`completion_tokens`). */
@Serializable
data class ClaudeMessagesResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
    val usage: ClaudeUsage? = null,
)

@Serializable
data class ClaudeContentBlock(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)
