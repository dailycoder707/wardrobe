package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.ImportQueueItemEntity
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.ImportQueueItem
import java.time.Instant

internal fun ImportQueueItemEntity.toDomain() =
    ImportQueueItem(
        id = id,
        sourceFilePath = sourceFilePath,
        stagingId = stagingId,
        status = status,
        errorMessage = errorMessage,
        savedGarmentId = savedGarmentId?.let(::GarmentId),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

internal fun ImportQueueItem.toEntity() =
    ImportQueueItemEntity(
        id = id,
        sourceFilePath = sourceFilePath,
        stagingId = stagingId,
        status = status,
        errorMessage = errorMessage,
        savedGarmentId = savedGarmentId?.value,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
