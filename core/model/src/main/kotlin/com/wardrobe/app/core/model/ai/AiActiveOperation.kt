package com.wardrobe.app.core.model.ai

import java.time.Instant

/**
 * A currently in-flight (not yet terminal) row from `AiJobManager`'s own
 * job ledger (`ai_jobs`, [AiJobStatus.PENDING]/[AiJobStatus.RUNNING] only) —
 * M18's "is AI currently doing something" signal. Projects the *existing*
 * job ledger into the UI layer rather than tracking AI activity a second
 * time; every dispatched capability call already writes/updates this row
 * via [com.wardrobe.app.core.ai.job.AiJobManager], M18 only reads it.
 *
 * Deliberately carries no `provider`/`model`/`confidence` — those aren't
 * known yet while a job is still [AiJobStatus.PENDING]/[AiJobStatus.RUNNING]
 * (only [AiResultProvenance], attached once a result exists, ever carries
 * them). Showing a provider name before the dispatch has resolved one would
 * be fabrication.
 */
data class AiActiveOperation(
    val capability: AiCapability,
    val status: AiJobStatus,
    val startedAt: Instant,
)
