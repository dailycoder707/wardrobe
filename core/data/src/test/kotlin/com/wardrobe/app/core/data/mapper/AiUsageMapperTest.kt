package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.AiCallLogEntity
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun entity(
    capability: AiCapability = AiCapability.GARMENT_METADATA,
    provider: String? = "openai",
    latencyMs: Long = 100L,
    outcome: AiCallOutcome = AiCallOutcome.SUCCESS,
    cacheHit: Boolean = false,
    estimatedCostMinorUnits: Long? = null,
) = AiCallLogEntity(
    capability = capability,
    provider = provider,
    model = null,
    latencyMs = latencyMs,
    outcome = outcome,
    confidence = null,
    estimatedInputTokens = null,
    estimatedOutputTokens = null,
    estimatedCostMinorUnits = estimatedCostMinorUnits,
    cacheHit = cacheHit,
    timestamp = 0L,
)

class AiUsageMapperTest {
    @Test
    fun `groups rows by capability and provider`() {
        val rows =
            listOf(
                entity(capability = AiCapability.GARMENT_METADATA, provider = "openai"),
                entity(capability = AiCapability.GARMENT_EXTRACTION, provider = "generic"),
            )

        val summaries = rows.toUsageSummaries()

        assertEquals(2, summaries.size)
    }

    @Test
    fun `averageLatencyMs excludes cache hits so it never understates real dispatch latency`() {
        val rows =
            listOf(
                entity(latencyMs = 200L, cacheHit = false),
                entity(latencyMs = 0L, cacheHit = true, outcome = AiCallOutcome.CACHE_HIT),
            )

        val summary = rows.toUsageSummaries().single()

        assertEquals(200L, summary.averageLatencyMs)
        assertEquals(1, summary.cacheHitCount)
    }

    @Test
    fun `averageLatencyMs is null when every call was a cache hit`() {
        val rows = listOf(entity(latencyMs = 0L, cacheHit = true, outcome = AiCallOutcome.CACHE_HIT))

        val summary = rows.toUsageSummaries().single()

        assertNull(summary.averageLatencyMs)
    }

    @Test
    fun `estimatedTotalCostMinorUnits is null rather than zero when no row has a real estimate`() {
        val rows = listOf(entity(estimatedCostMinorUnits = null))

        val summary = rows.toUsageSummaries().single()

        assertNull(summary.estimatedTotalCostMinorUnits)
    }

    @Test
    fun `estimatedTotalCostMinorUnits sums only the rows that carry a real estimate`() {
        val rows =
            listOf(
                entity(estimatedCostMinorUnits = 10L),
                entity(estimatedCostMinorUnits = 5L),
                entity(estimatedCostMinorUnits = null),
            )

        val summary = rows.toUsageSummaries().single()

        assertEquals(15L, summary.estimatedTotalCostMinorUnits)
    }

    @Test
    fun `successCount and failureCount classify outcomes correctly`() {
        val rows =
            listOf(
                entity(outcome = AiCallOutcome.SUCCESS),
                entity(outcome = AiCallOutcome.FAILURE),
                entity(outcome = AiCallOutcome.TIMEOUT),
                entity(outcome = AiCallOutcome.CACHE_HIT, cacheHit = true),
            )

        val summary = rows.toUsageSummaries().single()

        assertEquals(1, summary.successCount)
        assertEquals(2, summary.failureCount)
        assertEquals(4, summary.totalCalls)
    }

    @Test
    fun `toActivityEntry projects the real capability, provider, outcome, cache-hit and timestamp`() {
        val row =
            entity(capability = AiCapability.OUTFIT_STYLING, provider = "openai", outcome = AiCallOutcome.SUCCESS)
                .copy(timestamp = 1_700_000_000_000L, cacheHit = true)

        val activity = row.toActivityEntry()

        assertEquals(AiCapability.OUTFIT_STYLING, activity.capability)
        assertEquals("openai", activity.provider)
        assertEquals(AiCallOutcome.SUCCESS, activity.outcome)
        assertEquals(true, activity.cacheHit)
        assertEquals(1_700_000_000_000L, activity.timestamp.toEpochMilli())
    }
}
