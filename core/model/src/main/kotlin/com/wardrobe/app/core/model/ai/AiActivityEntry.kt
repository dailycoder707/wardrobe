package com.wardrobe.app.core.model.ai

import java.time.Instant

/**
 * One real, already-happened AI dispatch or cache hit — a thin, UI-ready
 * projection of `ai_call_log` (M15 Part 5's "Recent AI Activity"). Never
 * synthesized: this only ever reflects rows [com.wardrobe.app.core.model.ai.AiUsageSummary]
 * already aggregates from, just chronological rather than grouped.
 */
data class AiActivityEntry(
    val capability: AiCapability,
    val provider: String?,
    val outcome: AiCallOutcome,
    val cacheHit: Boolean,
    val timestamp: Instant,
)
