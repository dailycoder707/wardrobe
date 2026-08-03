package com.wardrobe.app.feature.capture.navigation

import kotlinx.serialization.Serializable

/** Camera preview + shutter for "Take Photo" — on success, enqueues one file
 * into the Room-backed import queue and navigates to [GarmentImportQueueRoute]. */
@Serializable
object GarmentCaptureRoute

/** Parameterless — the queue screen always reads
 * [com.wardrobe.app.core.domain.repository.ImportQueueRepository.observeQueue]
 * as its only source of truth, so "start a new import" (rows already
 * inserted by whoever navigated here) and "resume one interrupted by a
 * restart" are the same code path. */
@Serializable
object GarmentImportQueueRoute

/** [queueItemId] is the `import_queue_items` row id (not a `GarmentId` — no
 * garment exists yet until Save/Save as Draft commits one). */
@Serializable
data class GarmentReviewMetadataRoute(
    val queueItemId: Long,
)
