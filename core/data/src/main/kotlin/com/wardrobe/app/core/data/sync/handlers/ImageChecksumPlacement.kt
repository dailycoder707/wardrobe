package com.wardrobe.app.core.data.sync.handlers

import com.wardrobe.app.core.image.storage.ImageFileStore
import java.io.File

/**
 * Puts real bytes at [destination] before its owning row is written —
 * preferring, in order: bytes already there (nothing to do), a file the
 * caller already knows about with the same checksum (dedup — e.g. the same
 * photo already lives elsewhere), or the file the sync session's image-
 * transfer phase staged for this checksum earlier ([ImageFileStore.syncStagingFile]).
 * If none of those exist, the caller's row is still written (never block
 * sync on one photo) but the image itself stays missing until a future
 * sync's transfer phase succeeds — a real, stated gap, not a silent one.
 * Shared by every handler whose table carries a real image file
 * ([ImageMetadataSyncHandler], and Phase 10's `BodyProfileSyncHandler`/
 * `GarmentMaskSyncHandler`) so this logic exists exactly once.
 */
internal suspend fun placeFileForChecksum(
    checksum: String?,
    destination: File,
    imageFileStore: ImageFileStore,
    findExistingFileWithChecksum: suspend (String) -> File?,
) {
    if (checksum == null || destination.exists()) return
    val existingWithChecksum = findExistingFileWithChecksum(checksum)
    if (existingWithChecksum != null && existingWithChecksum.exists()) {
        existingWithChecksum.copyTo(destination, overwrite = true)
        return
    }
    val staged = imageFileStore.syncStagingFile(checksum)
    if (staged.exists()) {
        staged.copyTo(destination, overwrite = true)
        staged.delete()
    }
}
