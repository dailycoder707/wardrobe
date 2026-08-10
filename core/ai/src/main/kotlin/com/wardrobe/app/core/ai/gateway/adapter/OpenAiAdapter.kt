package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import javax.inject.Inject
import javax.inject.Singleton

/** OpenAI's real documented chat-completions-with-vision endpoint
 * (`POST {baseUrl}/v1/chat/completions`, `Authorization: Bearer <key>`) —
 * the reference vendor the "OpenAI-compatible" wire shape is named after. */
@Singleton
class OpenAiAdapter
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
