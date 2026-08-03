package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.WearEventEntity
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.common.WeatherCacheId
import com.wardrobe.app.core.model.wear.WearEvent
import java.time.Instant
import java.time.LocalDate

internal fun WearEventEntity.toDomain() =
    WearEvent(
        id = WearEventId(id),
        date = LocalDate.parse(date),
        garmentId = garmentId?.let(::GarmentId),
        outfitId = outfitId?.let(::OutfitId),
        weatherCacheId = weatherCacheId?.let(::WeatherCacheId),
        occasionId = occasionId?.let(::OccasionId),
        note = note,
        status = status,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

internal fun WearEvent.toEntity() =
    WearEventEntity(
        id = id.value,
        date = date.toString(),
        garmentId = garmentId?.value,
        outfitId = outfitId?.value,
        weatherCacheId = weatherCacheId?.value,
        occasionId = occasionId?.value,
        note = note,
        status = status,
        createdAt = createdAt.toEpochMilli(),
    )
