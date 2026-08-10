package com.wardrobe.app.core.model.ai

/**
 * The named cloud vendors `core:ai`'s Gateway can dispatch to (ADR-012) —
 * each maps to exactly one `ProviderAdapter` implementation. `GENERIC_REST`
 * is not a vendor at all but the documented multipart image-task contract
 * any self-hosted or third-party backend the user points at must implement.
 * Adding vendor #8 means adding one more entry here and one more adapter —
 * nothing else in the Gateway/router/settings layer changes shape. Lives in
 * `core:model` (not `core:ai`) alongside [AiProviderConfig] so
 * `core:datastore` can read/write provider preferences without depending on
 * `core:ai`.
 */
enum class AiVendor {
    OPENAI,
    AZURE_OPENAI,
    GEMINI,
    CLAUDE,
    OPENROUTER,
    OLLAMA,
    GENERIC_REST,
}

/**
 * The vendor's real, publicly documented API root (M24 real-device finding)
 * — used only to pre-fill Settings' Base URL field the instant a user picks
 * this vendor, so cloud dispatch is never silently unreachable just because
 * the field was left blank (`AiProviderConfig.isCloudReady` already
 * correctly requires a non-blank [AiProviderConfig.baseUrl]; nothing
 * previously told the user *why* it stayed on-device when they forgot to
 * fill this in). Never used to activate cloud on its own — consent and an
 * API key are still required exactly as before.
 *
 * `null` for the three vendors with no single correct default:
 * [AiVendor.AZURE_OPENAI] (the user's own Azure resource endpoint),
 * [AiVendor.OLLAMA] (a self-hosted address only the user knows — guessing
 * `localhost` would be actively wrong on a tablet, since that would mean
 * the tablet itself, not whatever machine is actually running Ollama), and
 * [AiVendor.GENERIC_REST] (fully custom by definition).
 */
fun AiVendor.defaultBaseUrl(): String? =
    when (this) {
        AiVendor.OPENAI -> "https://api.openai.com"
        AiVendor.GEMINI -> "https://generativelanguage.googleapis.com"
        AiVendor.CLAUDE -> "https://api.anthropic.com"
        AiVendor.OPENROUTER -> "https://openrouter.ai/api"
        AiVendor.AZURE_OPENAI, AiVendor.OLLAMA, AiVendor.GENERIC_REST -> null
    }
