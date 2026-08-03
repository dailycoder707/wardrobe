package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.GarmentId
import java.time.Instant

/**
 * One row's state in the Room-backed Add-to-Wardrobe import queue
 * (`core:database`'s `import_queue_items` table, device-local only — see
 * `ImportQueueRepository`'s KDoc). `SAVING` found on resume after a restart
 * falls back to [READY_FOR_REVIEW] rather than silently losing the user's
 * entered metadata — see `feature:capture`'s `GarmentImportQueueViewModel`.
 */
enum class ImportQueueItemStatus {
    PENDING,
    IMPORTING,
    REMOVING_BACKGROUND,
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
