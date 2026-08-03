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
     * only possible after a crash/interruption, since normal writes and DB
     * inserts happen together (`ImageRepositoryImpl.commitStagedImage`). */
    fun findOrphans(
        referencedPaths: Set<String>,
        filesOnDisk: List<File>,
    ): List<File> = filesOnDisk.filter { it.path !in referencedPaths }

    /** Staging directories older than [olderThan] with no corresponding
     * commit/discard — the user captured a photo, then closed or killed the
     * app before saving or discarding it. */
    fun findStaleStagingDirs(
        stagingDirs: List<File>,
        olderThan: Instant,
    ): List<File> = stagingDirs.filter { dir -> Instant.ofEpochMilli(dir.lastModified()).isBefore(olderThan) }
}
