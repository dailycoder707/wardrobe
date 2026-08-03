package com.wardrobe.app.core.image.storage

import com.wardrobe.app.core.model.garment.ImageType

/**
 * One fixed filename per [ImageType]. Deterministic and collision-free by
 * construction, not by hashing or randomness — see phase-5b-image-pipeline.md's
 * "File naming" section for why. Callers never invent a filename themselves.
 *
 * There is no separate "cropped" file on disk: ADR-007 only names three stored
 * variants (original/cutout/thumbnail), and a crop is applied in-memory to the
 * decoded original before the cutout/thumbnail stages run (`GarmentImagePipeline`)
 * rather than persisted as its own file. `original.jpg` always keeps the full,
 * uncropped frame, which is what makes every later derived file reproducible —
 * re-cropping later re-decodes the original, it doesn't need a saved crop file.
 */
object ImageFileNames {
    private const val ORIGINAL = "original.jpg"
    private const val CUTOUT = "cutout.webp"
    private const val THUMBNAIL = "thumb.webp"

    fun filenameFor(type: ImageType): String =
        when (type) {
            ImageType.ORIGINAL -> ORIGINAL
            ImageType.CUTOUT -> CUTOUT
            ImageType.THUMBNAIL -> THUMBNAIL
        }
}
