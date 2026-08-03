package com.wardrobe.app.core.data.image

import com.wardrobe.app.core.model.garment.StagedImage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seam between [ImageProcessingWorker] (writes a result) and
 * `ImageRepositoryImpl` (reads it for commit/discard/progress-mapping) —
 * in-memory only, not a Room table.
 *
 * **Known, documented limitation** (see phase-5b-image-pipeline.md /
 * `TECHNICAL_DEBT.md`): if the process dies between a successful capture and
 * the user committing or discarding it, this entry is lost. The temp files
 * remain on disk and are eventually reclaimed by the stale-staging cleanup
 * sweep, but that specific capture result must be redone. Accepted for a
 * single, foreground, seconds-long review step — not worth a dedicated
 * persistence table for.
 */
@Singleton
class StagedImageStore
    @Inject
    constructor() {
        private val staged = ConcurrentHashMap<String, StagedImage>()

        fun put(result: StagedImage) {
            staged[result.stagingId] = result
        }

        fun peek(stagingId: String): StagedImage? = staged[stagingId]

        fun remove(stagingId: String): StagedImage? = staged.remove(stagingId)
    }
