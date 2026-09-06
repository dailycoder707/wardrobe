package com.wardrobe.app.core.model.ai

/**
 * Where an AI-produced value actually came from (ADR-012's provenance
 * requirement) — `MANUAL` covers the case where the user edits a suggested
 * field, so a garment's metadata can later distinguish "the AI said this"
 * from "I changed it."
 */
enum class AiResultSource {
    ON_DEVICE,
    CLOUD,
    MANUAL,
}
