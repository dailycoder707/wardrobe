package com.wardrobe.app.core.ai.gateway

import com.wardrobe.app.core.ai.metrics.AiMetricEvent
import com.wardrobe.app.core.ai.metrics.AiMetrics

class FakeAiMetrics : AiMetrics {
    val recordedEvents = mutableListOf<AiMetricEvent>()

    override suspend fun record(event: AiMetricEvent) {
        recordedEvents.add(event)
    }
}
