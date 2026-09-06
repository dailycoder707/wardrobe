package com.wardrobe.app.core.ai.metrics

import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability

/**
 * What the [AiMetrics] interface records for every Gateway dispatch —
 * success, failure, timeout, or cache hit. Token/cost fields are
 * approximations (this app is vendor-agnostic and owns no vendor's real
 * tokenizer) and are only non-null when the caller could actually compute
 * them — never fabricated (Constitution rule 4, ADR-012 §8).
 */
data class AiMetricEvent(
    val capability: AiCapability,
    val provider: String?,
    val model: String?,
    val latencyMs: Long,
    val outcome: AiCallOutcome,
    val confidence: Float?,
    val estimatedInputTokens: Int? = null,
    val estimatedOutputTokens: Int? = null,
    val estimatedCostMinorUnits: Long? = null,
)
