package com.wardrobe.app.core.image.cleanup

import java.io.File
import java.time.Instant

/**
 * Pure functions backing `OrphanedImageCleanupWorker` (`core:data`) — no
 * Android/Room dependency, so both sweeps are testable with plain `java.io.File`
 * fixtures. See phase-5b-image-pipeline.md's "Storage cleanup" section.
 */
object OrphanedImageDetector {
    /** Files present on disk with no matching `image_metadata.filePath` row —
     * normally only possible after a crash/interruption. [olderThan] is a
     * required safety margin, not an optimization: `ImageRepositoryImpl.
     * commitStagedImage` moves a garment's files into their final location
     * and *then* inserts the matching `image_metadata` rows — a real (if
     * narrow, millisecond-scale) window where a legitimately-just-committed
     * file exists on disk with no row yet. Without an age check, this
     * periodic sweep running during that exact window would misidentify a
     * brand-new, valid file as an orphan and delete it — real, silent data
     * loss for a photo the user just saved. Requiring the file to already be
     * older than [olderThan] makes that practically impossible while still
     * catching genuine, long-lived orphans. */
    fun findOrphans(
        referencedPaths: Set<String>,
        filesOnDisk: List<File>,
        olderThan: Instant,
    ): List<File> =
        filesOnDisk.filter { file ->
            file.path !in referencedPaths && Instant.ofEpochMilli(file.lastModified()).isBefore(olderThan)
        }

    /** Staging directories older than [olderThan] with no corresponding
     * commit/discard — the user captured a photo, then closed or killed the
     * app before saving or discarding it. */
    fun findStaleStagingDirs(
        stagingDirs: List<File>,
        olderThan: Instant,
    ): List<File> = stagingDirs.filter { dir -> Instant.ofEpochMilli(dir.lastModified()).isBefore(olderThan) }
}
