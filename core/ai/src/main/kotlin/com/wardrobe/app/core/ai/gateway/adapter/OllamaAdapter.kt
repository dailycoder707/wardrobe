package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import javax.inject.Inject
import javax.inject.Singleton

/** Ollama exposes its own OpenAI-compatible endpoint at
 * `{baseUrl}/v1/chat/completions` — a local/self-hosted server normally
 * needs no auth at all, so an empty API key is sent as no `Authorization`
 * header rather than a literal `Bearer ` prefix with nothing after it. */
@Singleton
class OllamaAdapter
    @Inject
    constructor(
        private val service: OpenAiCompatibleService,
    ) : VisionPromptAdapter {
        override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
            val url = "${request.baseUrl.trimEnd('/')}/v1/chat/completions"
            val headers =
                if (request.apiKey.isBlank()) {
                    emptyMap()
                } else {
                    mapOf("Authorization" to "Bearer ${request.apiKey}")
                }
            return runChatCompletions(service, url, headers, request)
        }
    }
