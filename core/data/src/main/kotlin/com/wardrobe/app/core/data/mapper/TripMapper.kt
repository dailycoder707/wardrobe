package com.wardrobe.app.core.data.mapper

import com.wardrobe.app.core.database.entity.PackingListItemEntity
import com.wardrobe.app.core.database.entity.TripActivityEntity
import com.wardrobe.app.core.database.entity.TripEntity
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import java.time.Instant
import java.time.LocalDate

internal fun TripEntity.toDomain(activities: List<TripActivityEntity>) =
    Trip(
        id = TripId(id),
        name = name,
        destination = destination,
        dateRange = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
        activities = activities.map { it.activityTag },
        luggageSize = luggageSize,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

internal fun Trip.toEntity() =
    TripEntity(
        id = id.value,
        name = name,
        destination = destination,
        startDate = dateRange.start.toString(),
        endDate = dateRange.end.toString(),
        luggageSize = luggageSize,
        createdAt = createdAt.toEpochMilli(),
    )

internal fun PackingListItemEntity.toDomain() =
    PackingListItem(
        id = PackingListItemId(id),
        tripId = TripId(tripId),
        garmentId = garmentId?.let(::GarmentId),
        freeTextName = freeTextName,
        category = category,
        isPacked = isPacked,
        rationale = rationale,
    )

internal fun PackingListItem.toEntity() =
    PackingListItemEntity(
        id = id.value,
        tripId = tripId.value,
        garmentId = garmentId?.value,
        freeTextName = freeTextName,
        category = category,
        isPacked = isPacked,
        rationale = rationale,
    )
