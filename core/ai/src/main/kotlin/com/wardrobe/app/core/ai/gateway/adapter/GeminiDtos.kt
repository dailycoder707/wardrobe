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

/** Gemini's `GET {baseUrl}/v1beta/models` response. Consulted only after a
 * `generateContent` call has already failed with "model not found", to turn
 * that dead end into a list of names the user's own key genuinely can call
 * — the app can't know a key's model entitlements any other way, and
 * guessing a replacement model would be exactly the fabrication this
 * project's rules forbid. */
@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModel> = emptyList(),
)

@Serializable
data class GeminiModel(
    val name: String? = null,
    @SerialName("supportedGenerationMethods") val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
data class GeminiUsageMetadata(
    @SerialName("promptTokenCount") val promptTokenCount: Int? = null,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Int? = null,
)
