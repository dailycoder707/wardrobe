package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.AiCallLogEntity
import com.wardrobe.app.core.database.entity.AiJobEntity
import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import com.wardrobe.app.core.model.ai.AiUsageSummary
import java.time.Instant

/** Groups the raw call log into one [AiUsageSummary] per capability+provider
 * pair — see that type's KDoc for why cache hits are excluded from the
 * latency average and why cost is `null` rather than `0` when no row
 * carries a real estimate. */
fun List<AiCallLogEntity>.toUsageSummaries(): List<AiUsageSummary> =
    groupBy { it.capability to it.provider }
        .map { (key, rows) -> rows.toUsageSummary(key.first, key.second) }
        .sortedBy { it.capability.ordinal }

private fun List<AiCallLogEntity>.toUsageSummary(
    capability: AiCapability,
    provider: String?,
): AiUsageSummary {
    val realDispatches = filterNot { it.cacheHit }
    val costs = mapNotNull { it.estimatedCostMinorUnits }
    val averageLatencyMs =
        realDispatches.takeIf { it.isNotEmpty() }?.let { rows ->
            rows.map { it.latencyMs }.average()
        }
    return AiUsageSummary(
        capability = capability,
        provider = provider,
        totalCalls = size,
        cacheHitCount = count { it.cacheHit },
        successCount = count { it.outcome == AiCallOutcome.SUCCESS },
        failureCount = count { it.outcome == AiCallOutcome.FAILURE || it.outcome == AiCallOutcome.TIMEOUT },
        averageLatencyMs = averageLatencyMs?.toLong(),
        estimatedTotalCostMinorUnits = costs.takeIf { it.isNotEmpty() }?.sum(),
    )
}

/** [AiCallLogDao.observeAll] already orders newest-first — this only
 * projects each row into the smaller, UI-ready shape a "Recent AI Activity"
 * list needs, no reordering. */
fun AiCallLogEntity.toActivityEntry(): AiActivityEntry =
    AiActivityEntry(
        capability = capability,
        provider = provider,
        outcome = outcome,
        cacheHit = cacheHit,
        timestamp = Instant.ofEpochMilli(timestamp),
    )

private val ACTIVE_JOB_STATUSES = setOf(AiJobStatus.PENDING, AiJobStatus.RUNNING)

/** [AiJobDao.observeAll] already orders newest-first by `createdAt` — this
 * keeps only the genuinely not-yet-terminal rows (M18's "is AI currently
 * doing something" signal) and projects them to the smaller UI-facing
 * shape. */
fun List<AiJobEntity>.toActiveOperations(): List<AiActiveOperation> =
    filter { it.status in ACTIVE_JOB_STATUSES }
        .map { entity ->
            AiActiveOperation(
                capability = entity.capability,
                status = entity.status,
                startedAt = Instant.ofEpochMilli(entity.createdAt),
            )
        }
