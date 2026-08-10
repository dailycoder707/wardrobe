package com.wardrobe.app.core.ai.gateway.adapter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Gemini's real documented `generateContent` response shape — genuinely
 * different from the OpenAI-compatible one (nested `candidates[0].content
 * .parts[0].text` rather than `choices[0].message.content`), which is why
 * this needs its own adapter rather than reusing [OpenAiCompatibleService]. */
@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("usageMetadata") val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
data class GeminiPart(
    val text: String? = null,
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount") val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int? = null,
)
