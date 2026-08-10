package com.wardrobe.app.core.ai.metrics

import com.wardrobe.app.core.database.dao.AiCallLogDao
import com.wardrobe.app.core.database.entity.AiCallLogEntity
import com.wardrobe.app.core.model.ai.AiCallOutcome
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The default (only) [AiMetrics] implementation — persists into
 * `ai_call_log`. Cost tracking (the Settings "AI Usage" view, ADR-012 §8) is
 * a *consumer* of this table, not a second write path. */
@Singleton
class RoomAiMetricsRecorder
    @Inject
    constructor(
        private val aiCallLogDao: AiCallLogDao,
        private val clock: Clock,
    ) : AiMetrics {
        override suspend fun record(event: AiMetricEvent) {
            aiCallLogDao.insert(
                AiCallLogEntity(
                    capability = event.capability,
                    provider = event.provider,
                    model = event.model,
                    latencyMs = event.latencyMs,
                    outcome = event.outcome,
                    confidence = event.confidence,
                    estimatedInputTokens = event.estimatedInputTokens,
                    estimatedOutputTokens = event.estimatedOutputTokens,
                    estimatedCostMinorUnits = event.estimatedCostMinorUnits,
                    cacheHit = event.outcome == AiCallOutcome.CACHE_HIT,
                    timestamp = clock.millis(),
                ),
            )
        }
    }
