package com.wardrobe.app.core.ai.gateway

import com.wardrobe.app.core.ai.metrics.AiMetricEvent
import com.wardrobe.app.core.ai.metrics.AiMetrics
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import java.time.Clock
import java.time.Instant

/** The outcome half of an [AiMetricEvent] — bundled so `record*` helpers
 * stay under this project's `LongParameterList` threshold. */
internal data class DispatchOutcome(
    val latencyMs: Long,
    val outcome: AiCallOutcome,
    val confidence: Float? = null,
    val estimatedInputTokens: Int? = null,
    val estimatedOutputTokens: Int? = null,
)

internal fun provenanceFor(
    context: AiDispatchContext,
    promptVersion: String,
    clock: Clock,
    cacheHit: Boolean,
    latencyMs: Long,
): AiResultProvenance =
    AiResultProvenance(
        source = AiResultSource.CLOUD,
        provider = context.config.vendor?.name,
        model = context.config.model,
        promptVersion = promptVersion,
        generatedAt = Instant.ofEpochMilli(clock.millis()),
        cacheHit = cacheHit,
        latencyMs = latencyMs,
    )

internal suspend fun recordCacheHit(
    metrics: AiMetrics,
    context: AiDispatchContext,
) {
    metrics.record(
        AiMetricEvent(
            capability = context.capability,
            provider = context.config.vendor?.name,
            model = context.config.model,
            latencyMs = 0,
            outcome = AiCallOutcome.CACHE_HIT,
            confidence = null,
        ),
    )
}

internal suspend fun recordMetric(
    metrics: AiMetrics,
    context: AiDispatchContext,
    outcome: DispatchOutcome,
) {
    metrics.record(
        AiMetricEvent(
            capability = context.capability,
            provider = context.config.vendor?.name,
            model = context.config.model,
            latencyMs = outcome.latencyMs,
            outcome = outcome.outcome,
            confidence = outcome.confidence,
            estimatedInputTokens = outcome.estimatedInputTokens,
            estimatedOutputTokens = outcome.estimatedOutputTokens,
            estimatedCostMinorUnits =
                estimateCostMinorUnits(
                    context.config.costRatePerThousandTokens,
                    outcome.estimatedInputTokens,
                    outcome.estimatedOutputTokens,
                ),
        ),
    )
}
