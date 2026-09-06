package com.wardrobe.app.core.ai.di

import com.wardrobe.app.core.ai.gateway.ImageTaskAdapter
import com.wardrobe.app.core.ai.gateway.VisionPromptAdapter
import com.wardrobe.app.core.ai.gateway.adapter.AzureOpenAiAdapter
import com.wardrobe.app.core.ai.gateway.adapter.ClaudeAdapter
import com.wardrobe.app.core.ai.gateway.adapter.GeminiAdapter
import com.wardrobe.app.core.ai.gateway.adapter.GenericRestAdapter
import com.wardrobe.app.core.ai.gateway.adapter.OllamaAdapter
import com.wardrobe.app.core.ai.gateway.adapter.OpenAiAdapter
import com.wardrobe.app.core.ai.gateway.adapter.OpenRouterAdapter
import com.wardrobe.app.core.model.ai.AiVendor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * The concrete vendor-adapter registrations (ADR-012 §1) — adding vendor #8
 * means adding one adapter class and one `@Binds` entry here, nothing else
 * in [com.wardrobe.app.core.ai.gateway.DefaultAiGateway] or the routers
 * (M7) changes. [GenericRestAdapter] is the only [ImageTaskAdapter] — there
 * is no industry-standard shape for image-in/image-out tasks the way
 * chat-vision has one, so [AiVendor.GENERIC_REST] is the only vendor that
 * capability accepts.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdapterBindsModule {
    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.OPENAI)
    abstract fun bindOpenAiAdapter(impl: OpenAiAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.AZURE_OPENAI)
    abstract fun bindAzureOpenAiAdapter(impl: AzureOpenAiAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.GEMINI)
    abstract fun bindGeminiAdapter(impl: GeminiAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.CLAUDE)
    abstract fun bindClaudeAdapter(impl: ClaudeAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.OPENROUTER)
    abstract fun bindOpenRouterAdapter(impl: OpenRouterAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.OLLAMA)
    abstract fun bindOllamaAdapter(impl: OllamaAdapter): VisionPromptAdapter

    @Binds
    @IntoMap
    @AiVendorKey(AiVendor.GENERIC_REST)
    abstract fun bindGenericRestAdapter(impl: GenericRestAdapter): ImageTaskAdapter
}
