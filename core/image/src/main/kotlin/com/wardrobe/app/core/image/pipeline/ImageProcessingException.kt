package com.wardrobe.app.core.image.pipeline

import com.wardrobe.app.core.model.garment.ProcessingStage

/** Thrown by any [GarmentImagePipeline] stage; caught by `ImageProcessingWorker`
 * (`core:data`) and mapped to `Result.failure()`, the same shape
 * `BackupExportWorker`/`BackupRestoreWorker` already use for their own
 * exceptions — see phase-5b-image-pipeline.md's "Error handling" section. */
class ImageProcessingException(
    val stage: ProcessingStage,
    cause: Throwable,
) : Exception("Image processing failed at $stage: ${cause.message}", cause)
