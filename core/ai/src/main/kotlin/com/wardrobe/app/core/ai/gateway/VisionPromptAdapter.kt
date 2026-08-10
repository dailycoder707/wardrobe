package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap

/**
 * One implementation per named vendor (ADR-012 §1) — translates this
 * normalized request into that vendor's real documented chat-vision request
 * shape and auth scheme, and translates the response back. Nothing outside
 * an adapter file knows the vendor's wire format; the
 * [com.wardrobe.app.core.ai.gateway.AiGateway] only ever calls this
 * interface.
 */
interface VisionPromptAdapter {
    suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult
}

/** [expectJsonResponse] (M24) — set only by callers whose prompt asks the
 * model for JSON-only output (Garment Metadata, Outfit Styling), never by
 * free-text callers like the connection-test ping ("Respond with the single
 * word OK" is not valid JSON). When `true`, an adapter that has a real
 * provider-native structured-output mode (OpenAI-compatible's
 * `response_format`, Gemini's `responseMimeType`) uses it instead of relying
 * on prompt compliance alone; an adapter with no such mode (Claude, which
 * has no JSON-schema response mode) simply ignores the flag and stays
 * prompt-only — never a fabricated capability. */
data class VisionPromptAdapterRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String?,
    val systemPrompt: String,
    val userPrompt: String,
    val image: Bitmap,
    val expectJsonResponse: Boolean = false,
)

sealed interface VisionPromptAdapterResult {
    /** [estimatedInputTokens]/[estimatedOutputTokens] are `null` when the
     * vendor's response doesn't report real usage figures — never
     * fabricated (Constitution rule 4). */
    data class Success(
        val rawResponseText: String,
        val estimatedInputTokens: Int?,
        val estimatedOutputTokens: Int?,
    ) : VisionPromptAdapterResult

    data class Failure(
        val reason: String,
    ) : VisionPromptAdapterResult
}
