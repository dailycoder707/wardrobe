package com.wardrobe.app.core.model.ai

/**
 * The capability registry (Add-to-Wardrobe v2 / ADR-012) — the single key
 * every provider-preference row, cache row, job row, and cost-log row is
 * keyed by. Adding a sixth capability later means adding one value here and
 * one on-device implementation; it never requires touching the gateway,
 * routers, or settings screen shape.
 */
enum class AiCapability {
    GARMENT_EXTRACTION,
    GARMENT_RECONSTRUCTION,
    GARMENT_METADATA,
    OUTFIT_STYLING,
    VIRTUAL_TRY_ON,
}
