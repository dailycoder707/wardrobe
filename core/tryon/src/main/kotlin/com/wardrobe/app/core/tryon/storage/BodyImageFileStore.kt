package com.wardrobe.app.core.tryon.storage

import android.content.Context
import com.wardrobe.app.core.model.tryon.BodyPose
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `images/body/` directory layout — a sibling root to
 * `images/{garmentId}/` (`core:image`'s `ImageFileStore`), deliberately a
 * **separate** class rather than an extension of it: a body reference
 * photo or garment mask is not a garment `ImageType.ORIGINAL`/`CUTOUT`/
 * `THUMBNAIL`, and mixing the two concepts into one `ImageType`-keyed store
 * would be a type lie. Never picked up by
 * [com.wardrobe.app.core.image.storage.ImageFileStore.allImageFilesOnDisk]'s
 * garment-orphan sweep, since it lives outside `images/{garmentId}/`
 * entirely.
 */
@Singleton
class BodyImageFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun bodyRoot(): File = File(File(context.filesDir, "images"), "body")

        fun profileDir(bodyProfileId: Long): File = File(bodyRoot(), bodyProfileId.toString())

        fun masksDir(bodyProfileId: Long): File = File(profileDir(bodyProfileId), "masks")

        fun photoFile(
            bodyProfileId: Long,
            pose: BodyPose,
        ): File = File(profileDir(bodyProfileId), "${pose.name.lowercase()}.jpg")

        fun maskFile(
            bodyProfileId: Long,
            garmentId: Long,
        ): File = File(masksDir(bodyProfileId), "$garmentId.png")

        fun ensureExists(dir: File): File {
            dir.mkdirs()
            return dir
        }

        /** Deletes every photo/mask file for one body profile in a single
         * operation — the same "one entry point that touches these files"
         * discipline `ImageFileStore.deleteGarmentDirectory` already
         * established. Room's own `CASCADE` removes the rows; it never
         * touches files on disk. */
        fun deleteProfileDirectory(bodyProfileId: Long): Boolean = profileDir(bodyProfileId).deleteRecursively()
    }
