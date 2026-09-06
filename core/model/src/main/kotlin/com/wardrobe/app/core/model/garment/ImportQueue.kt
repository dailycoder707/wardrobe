package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.GarmentId
import java.time.Instant

/**
 * One row's state in the Room-backed Add-to-Wardrobe import queue
 * (`core:database`'s `import_queue_items` table, device-local only — see
 * `ImportQueueRepository`'s KDoc). `SAVING` found on resume after a restart
 * falls back to [READY_FOR_REVIEW] rather than silently losing the user's
 * entered metadata — see `feature:capture`'s `GarmentImportQueueViewModel`.
 *
 * [EXTRACTING_GARMENT] through [GENERATING_METADATA] are Add-to-Wardrobe
 * v2's (ADR-012) real, per-stage progress statuses — replacing v1's single
 * `REMOVING_BACKGROUND` catch-all so the queue screen can show the user
 * what's actually happening (Constitution rule 4: real progress, never a
 * generic spinner standing in for four different operations).
 */
enum class ImportQueueItemStatus {
    PENDING,
    IMPORTING,
    EXTRACTING_GARMENT,
    ENHANCING_PRESENTATION,
    RECONSTRUCTING_OCCLUSIONS,
    GENERATING_METADATA,
    READY_FOR_REVIEW,
    SAVING,
    COMPLETED,
    FAILED,
}

/**
 * The queue screen's single source of truth for one item — this table (not
 * ViewModel-held state) is what survives an app restart or crash mid-import.
 * [stagingId] is null until [ImageRepository][com.wardrobe.app.core.domain.repository.ImageRepository]
 * .stageImage begins; [savedGarmentId] is set only once [status] reaches
 * [ImportQueueItemStatus.COMPLETED].
 */
data class ImportQueueItem(
    val id: Long,
    val sourceFilePath: String,
    val stagingId: String?,
    val status: ImportQueueItemStatus,
    val errorMessage: String?,
    val savedGarmentId: GarmentId?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
