package com.wardrobe.app.core.model.ai

/** What happened when the AI Gateway dispatched (or short-circuited) a
 * capability call — the shape `core:ai`'s `AiMetricEvent` emits and
 * `AiCallLogEntity` (`core:database`) persists. `CACHE_HIT` is recorded
 * distinctly from `SUCCESS` so cost/usage views can show a real cache-hit
 * rate rather than conflating "answered from cache" with "a real provider
 * call succeeded." */
enum class AiCallOutcome {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CACHE_HIT,
}
