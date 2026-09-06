package com.wardrobe.app.core.ai.metrics

/**
 * The single internal seam every AI dispatch reports through (ADR-012
 * §12) — the [com.wardrobe.app.core.ai.gateway.AiGateway], not individual
 * adapters, is the only caller. A narrow interface means adding a seventh
 * vendor adapter can't forget to emit metrics, and swapping *how* metrics
 * are stored/exported later never touches the Gateway or routers.
 */
interface AiMetrics {
    suspend fun record(event: AiMetricEvent)
}
