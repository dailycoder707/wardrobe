package com.wardrobe.app.core.ai.gateway.adapter

import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterRequest
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapterResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Azure OpenAI's real documented shape: the deployment name lives in the
 * URL path (`POST {baseUrl}/openai/deployments/{deployment}/chat/completions?api-version=...`),
 * not the request body, and auth is `api-key: <key>` rather than a Bearer
 * token — this project's `model` field is reused to mean "deployment name"
 * for this vendor specifically, since Azure has no separate concept in the
 * body. [AZURE_API_VERSION] is a fixed, disclosed choice (not user-
 * configurable yet) — see `TECHNICAL_DEBT.md`.
 */
@Singleton
class AzureOpenAiAdapter
    @Inject
    constructor(
        private val service: OpenAiCompatibleService,
    ) : VisionPromptAdapter {
        override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
            val deployment =
                request.model
                    ?: return VisionPromptAdapterResult.Failure("azure_openai_requires_a_deployment_name_in_model")
            val url =
                "${request.baseUrl.trimEnd('/')}/openai/deployments/$deployment/chat/completions" +
                    "?api-version=$AZURE_API_VERSION"
            val headers = mapOf("api-key" to request.apiKey)
            return runChatCompletions(service, url, headers, request)
        }

        private companion object {
            const val AZURE_API_VERSION = "2024-06-01"
        }
    }
