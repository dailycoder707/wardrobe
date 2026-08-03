package com.wardrobe.app.core.image.storage

import android.content.Context
import com.wardrobe.app.core.model.garment.ImageType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `images/` directory layout — see phase-5b-image-pipeline.md's
 * "Storage layout" section for the full rationale (directory-per-garment, kept
 * from ADR-007, over the type-keyed layout the master prompt sketched as an
 * example).
 */
@Singleton
class ImageFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun imagesRoot(): File = File(context.filesDir, "images")

        private fun tempRoot(): File = File(imagesRoot(), "temp")

        // No cacheRoot() — `images/cache/` is a reserved, deliberately unused
        // namespace (see phase-5b-image-pipeline.md's storage-layout note);
        // Coil's own disk cache lives at `context.cacheDir` instead.

        fun garmentDir(garmentId: Long): File = File(imagesRoot(), garmentId.toString())

        fun stagingDir(stagingId: String): File = File(tempRoot(), stagingId)

        /** A single checksum-keyed file under `temp/sync/` — Phase 8's image
         * transfer stages a received file here (its final garment/type
         * destination isn't known until the corresponding `image_metadata`
         * row is applied) before [com.wardrobe.app.core.image.hashing.ImageHasher]
         * confirms it and it's moved/copied into place. Deliberately a
         * separate namespace from [stagingDir], which is keyed by capture
         * session, not by content hash. */
        fun syncStagingFile(checksum: String): File = File(tempRoot(), "sync/$checksum")

        fun fileFor(
            dir: File,
            type: ImageType,
        ): File = File(dir, ImageFileNames.filenameFor(type))

        fun ensureExists(dir: File): File {
            dir.mkdirs()
            return dir
        }

        /** Deletes every file for one garment in a single operation — the
         * structural half of "never delete another garment's assets" (the other
         * half is that this is the *only* deletion entry point that touches
         * garment image files). Room's own `CASCADE` on `image_metadata` removes
         * the rows when a garment is deleted; it never touches files on disk,
         * which is why this call exists as a separate step. */
        fun deleteGarmentDirectory(garmentId: Long): Boolean = garmentDir(garmentId).deleteRecursively()

        fun deleteStagingDirectory(stagingId: String): Boolean = stagingDir(stagingId).deleteRecursively()

        /**
         * Moves every existing file out of `temp/{stagingId}/` into
         * `images/{garmentId}/`, keyed by [ImageType]. A plain [File.renameTo] is
         * sufficient (and atomic) since both directories live under the same
         * `filesDir` volume — no cross-filesystem copy is ever needed here.
         * Returns the final [File] for each [ImageType] that existed in staging.
         */
        fun moveStagingToGarment(
            stagingId: String,
            garmentId: Long,
        ): Map<ImageType, File> {
            val staging = stagingDir(stagingId)
            val destination = ensureExists(garmentDir(garmentId))
            val moved =
                ImageType.entries
                    .mapNotNull { type ->
                        val source = fileFor(staging, type)
                        if (!source.exists()) return@mapNotNull null
                        val target = fileFor(destination, type)
                        check(source.renameTo(target)) { "Failed to move ${source.path} to ${target.path}" }
                        type to target
                    }.toMap()
            staging.deleteRecursively()
            return moved
        }

        /** Every image file currently on disk, across every garment directory —
         * excludes `temp/` and `cache/`, which are not garment-owned. Read by
         * `OrphanedImageCleanupWorker`'s true-orphan sweep. */
        fun allImageFilesOnDisk(): List<File> =
            imagesRoot()
                .listFiles { file -> file.isDirectory && file.name != "temp" && file.name != "cache" }
                ?.flatMap { garmentDir -> garmentDir.listFiles()?.toList().orEmpty() }
                .orEmpty()

        /** Every staging directory currently pending commit/discard — read by
         * `OrphanedImageCleanupWorker`'s stale-staging sweep. */
        fun allStagingDirectories(): List<File> = tempRoot().listFiles { file -> file.isDirectory }?.toList().orEmpty()
    }
