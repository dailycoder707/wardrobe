package com.wardrobe.app.feature.settings.aiproviders

import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor

/** Human-readable labels for the enums [AiProvidersScreen] renders — split
 * out of that file (Detekt's file-level `TooManyFunctions`). */

internal fun AiCapability.displayName(): String =
    when (this) {
        AiCapability.GARMENT_EXTRACTION -> "Garment Extraction"
        AiCapability.GARMENT_RECONSTRUCTION -> "Garment Reconstruction"
        AiCapability.GARMENT_METADATA -> "Garment Metadata"
        AiCapability.OUTFIT_STYLING -> "Outfit Styling"
        AiCapability.VIRTUAL_TRY_ON -> "Virtual Try-On"
    }

internal fun AiProviderMode.displayName(): String =
    when (this) {
        AiProviderMode.ON_DEVICE -> "On-Device"
        AiProviderMode.CLOUD -> "Cloud"
    }

internal fun AiVendor.displayName(): String =
    when (this) {
        AiVendor.OPENAI -> "OpenAI"
        AiVendor.AZURE_OPENAI -> "Azure OpenAI"
        AiVendor.GEMINI -> "Gemini"
        AiVendor.CLAUDE -> "Claude"
        AiVendor.OPENROUTER -> "OpenRouter"
        AiVendor.OLLAMA -> "Ollama"
        AiVendor.GENERIC_REST -> "Generic REST"
    }
