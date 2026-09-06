package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import javax.inject.Inject
import javax.inject.Singleton

/** OpenRouter proxies many vendors (including Gemini/Claude) behind a
 * single OpenAI-compatible endpoint — `POST {baseUrl}/v1/chat/completions`,
 * `Authorization: Bearer <key>`, same as [OpenAiAdapter]. The user's
 * configured base URL is expected to be OpenRouter's API root
 * (`https://openrouter.ai/api`), matching this suffix. */
@Singleton
class OpenRouterAdapter
    @Inject
    constructor(
        private val service: OpenAiCompatibleService,
    ) : VisionPromptAdapter {
        override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
            val url = "${request.baseUrl.trimEnd('/')}/v1/chat/completions"
            val headers = mapOf("Authorization" to "Bearer ${request.apiKey}")
            return runChatCompletions(service, url, headers, request)
        }
    }
