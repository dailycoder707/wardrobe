package com.wardrobe.app.core.model.ai

/** [AiJobEntity][com.wardrobe.app.core.database.entity.AiJobEntity]'s
 * status — the generalized, capability-agnostic counterpart of
 * `ImportQueueItemStatus`'s per-photo states (`AiJobManager` tracks one row
 * per dispatched capability call, not per garment-import). */
enum class AiJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}
