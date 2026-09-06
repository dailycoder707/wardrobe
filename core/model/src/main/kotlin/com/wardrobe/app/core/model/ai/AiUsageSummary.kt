package com.wardrobe.app.core.model.ai

/**
 * One capability+provider row in the Settings screen's "AI Usage" section
 * (ADR-012 §8/§12) — a local aggregate over `ai_call_log`, never a second
 * write path (cost tracking/usage display are both *consumers* of the same
 * metrics stream). [averageLatencyMs] is computed only over real dispatches
 * (cache hits excluded — their near-zero latency would silently understate
 * genuine provider latency); `null` when every call for this row was a
 * cache hit. [estimatedTotalCostMinorUnits] is `null` whenever no call in
 * this row carries a cost estimate (the user never supplied a cost-rate for
 * this capability) — never a fabricated `0` (Constitution rule 4).
 */
data class AiUsageSummary(
    val capability: AiCapability,
    val provider: String?,
    val totalCalls: Int,
    val cacheHitCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val averageLatencyMs: Long?,
    val estimatedTotalCostMinorUnits: Long?,
)
