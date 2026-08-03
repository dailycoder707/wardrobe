package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.intelligence.ConflictReason
import com.wardrobe.app.core.model.trip.Trip
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/* Phase 9 — WardrobeIntelligenceRepositoryImpl's calendar-conflict
 * builders, split out for the same TooManyFunctions-avoidance reason as
 * the other *Builders.kt sibling files in this package. */

// LocalDate.EPOCH requires API 34; this project's minSdk is 26, so the epoch
// date is spelled out instead (see phase-5a-data-layer.md's minSdk record).
@Suppress("MagicNumber")
private val EPOCH_START_DATE: LocalDate = LocalDate.of(1970, 1, 1)

internal fun allTimeRange(clock: Clock): DateRange = DateRange(EPOCH_START_DATE, LocalDate.now(clock).plusYears(1))

internal fun lookAheadRange(
    lookAheadDays: Int,
    clock: Clock,
): DateRange {
    val today = LocalDate.now(clock)
    return DateRange(today, today.plusDays(lookAheadDays.toLong()))
}

internal suspend fun buildCalendarConflicts(
    events: List<WearEvent>,
    garments: List<Garment>,
    tripRepository: TripRepository,
): List<CalendarConflict> {
    val planned = events.filter { it.status == WearEventStatus.PLANNED }
    val garmentsById = garments.associateBy { it.id }
    return duplicatePlannedOutfitConflicts(planned) +
        laundryConflicts(planned, garmentsById) +
        packedElsewhereConflicts(planned, tripRepository)
}

private fun duplicatePlannedOutfitConflicts(planned: List<WearEvent>): List<CalendarConflict> =
    planned
        .filter { it.outfitId != null }
        .groupBy { it.outfitId }
        .filterValues { it.size > 1 }
        .flatMap { (outfitId, entries) ->
            entries.map { event ->
                CalendarConflict(
                    date = event.date,
                    reason = ConflictReason.DUPLICATE_PLANNED_OUTFIT,
                    outfitId = outfitId,
                    garmentId = null,
                    message = "This outfit is already planned for another day.",
                )
            }
        }

private fun laundryConflicts(
    planned: List<WearEvent>,
    garmentsById: Map<GarmentId, Garment>,
): List<CalendarConflict> =
    planned.mapNotNull { event ->
        val garment = event.garmentId?.let { garmentsById[it] }
        if (garment == null || !garment.isInLaundry) {
            null
        } else {
            CalendarConflict(
                date = event.date,
                reason = ConflictReason.GARMENT_IN_LAUNDRY,
                outfitId = null,
                garmentId = garment.id,
                message = "This item is currently marked as in the laundry.",
            )
        }
    }

/** Checks each garment-based planned event against every trip's packing
 * list — a real (not fabricated) suspend lookup, since this whole builder
 * chain runs inside the outer `Flow.map`'s transform, which
 * `kotlinx.coroutines.flow.map` supports directly (its `transform`
 * parameter is itself `suspend`). */
private suspend fun packedElsewhereConflicts(
    planned: List<WearEvent>,
    tripRepository: TripRepository,
): List<CalendarConflict> {
    val garmentEvents = planned.filter { it.garmentId != null }
    if (garmentEvents.isEmpty()) return emptyList()
    val trips = tripRepository.observeTrips().first()
    return garmentEvents.mapNotNull { event -> packedElsewhereConflictFor(event, trips, tripRepository) }
}

private suspend fun packedElsewhereConflictFor(
    event: WearEvent,
    trips: List<Trip>,
    tripRepository: TripRepository,
): CalendarConflict? {
    val packedTrip =
        trips.firstOrNull { trip ->
            val overlapsEventDate =
                !event.date.isBefore(trip.dateRange.start) && !event.date.isAfter(trip.dateRange.end)
            !overlapsEventDate &&
                tripRepository
                    .observePackingList(trip.id)
                    .first()
                    .any { it.garmentId == event.garmentId && it.isPacked }
        } ?: return null
    val tripName = packedTrip.name ?: packedTrip.destination
    return CalendarConflict(
        date = event.date,
        reason = ConflictReason.GARMENT_PACKED_ELSEWHERE,
        outfitId = null,
        garmentId = event.garmentId,
        message = "This item is packed for $tripName and won't be available on ${event.date}.",
    )
}
