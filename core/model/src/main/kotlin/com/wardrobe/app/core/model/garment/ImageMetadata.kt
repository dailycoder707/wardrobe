package com.wardrobe.app.core.model.garment

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.ImageMetadataId
import java.time.Instant

enum class ImageType { ORIGINAL, CUTOUT, THUMBNAIL }

/** One stored image file for a garment — see ADR-007 for the storage strategy. */
data class ImageMetadata(
    val id: ImageMetadataId,
    val garmentId: GarmentId,
    val type: ImageType,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: String,
    val checksum: String?,
    val createdAt: Instant,
)
