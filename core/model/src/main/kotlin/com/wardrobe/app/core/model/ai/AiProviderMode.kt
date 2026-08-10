package com.wardrobe.app.core.model.ai

/** Per-capability user choice (ADR-012) — `ON_DEVICE` is always the
 * default and the automatic fallback when `CLOUD` is selected but
 * misconfigured, unconsented, or unreachable. */
enum class AiProviderMode {
    ON_DEVICE,
    CLOUD,
}
