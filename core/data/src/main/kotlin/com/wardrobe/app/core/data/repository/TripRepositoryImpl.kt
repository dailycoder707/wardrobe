package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.mapper.toDomain
import com.wardrobe.app.core.data.mapper.toEntity
import com.wardrobe.app.core.database.dao.TripDao
import com.wardrobe.app.core.database.entity.TripActivityEntity
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private const val LONG_TRIP_DAY_THRESHOLD = 3

/**
 * [stylingEngineRepository] is injected as [dagger.Lazy] rather than
 * directly — `StylingEngineRepositoryImpl` itself injects [TripRepository]
 * (to resolve which garments are currently packed away, Phase 6), so a plain
 * constructor injection here would create a genuine Dagger dependency
 * cycle (`TripRepositoryImpl` → `StylingEngineRepository` →
 * `StylingEngineRepositoryImpl` → `TripRepository` → `TripRepositoryImpl`).
 * `Lazy` defers resolution until [generatePackingSuggestions] actually calls
 * `.get()`, which happens well after both objects exist — the standard
 * Dagger pattern for breaking a constructor-time cycle, not a workaround.
 */
class TripRepositoryImpl
    @Inject
    constructor(
        private val dao: TripDao,
        private val stylingEngineRepository: dagger.Lazy<StylingEngineRepository>,
    ) : TripRepository {
        // Recomputes each trip's activities on every emission — acceptable at this
        // entity's realistic scale (a personal wardrobe's trip count), see
        // phase-5a-data-layer.md.
        override fun observeTrips(): Flow<List<Trip>> =
            dao.observeAll().map { trips -> trips.map { it.toDomain(dao.getActivities(it.id)) } }

        override suspend fun getTrip(id: TripId): Trip? {
            val entity = dao.getById(id.value) ?: return null
            return entity.toDomain(dao.getActivities(entity.id))
        }

        override suspend fun saveTrip(trip: Trip): TripId {
            val now = System.currentTimeMillis()
            val id =
                if (trip.id.value == 0L) {
                    dao.insert(
                        trip.toEntity().copy(
                            id = 0,
                            syncId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            updatedAt = now,
                        ),
                    )
                } else {
                    val existingSyncId = dao.getById(trip.id.value)?.syncId.orEmpty()
                    dao.update(trip.toEntity().copy(syncId = existingSyncId, updatedAt = now))
                    trip.id.value
                }
            dao.clearActivities(id)
            if (trip.activities.isNotEmpty()) {
                dao.insertActivities(
                    trip.activities.map {
                        TripActivityEntity(
                            tripId = id,
                            activityTag = it,
                            syncId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            updatedAt = now,
                        )
                    },
                )
            }
            return TripId(id)
        }

        override suspend fun deleteTrip(id: TripId) = dao.deleteById(id.value)

        override fun observePackingList(tripId: TripId): Flow<List<PackingListItem>> =
            dao.observePackingList(tripId.value).map { items -> items.map { it.toDomain() } }

        /** Replaces the packing list wholesale — see phase-5a-data-layer.md and
         * TripDao.clearPackingList's own note on why a merge was rejected. */
        override suspend fun savePackingList(
            tripId: TripId,
            items: List<PackingListItem>,
        ) {
            dao.clearPackingList(tripId.value)
            if (items.isNotEmpty()) {
                val now = System.currentTimeMillis()
                dao.insertPackingListItems(
                    items.map {
                        it.toEntity().copy(
                            id = 0,
                            syncId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            updatedAt = now,
                        )
                    },
                )
            }
        }

        override suspend fun setPacked(
            itemId: PackingListItemId,
            isPacked: Boolean,
        ) {
            // PackingListItemEntity has no direct "getById" — read the owning trip's
            // list isn't available without a tripId here, so this updates via a
            // targeted query instead of a read-modify-write round trip.
            dao.setPackedState(itemId.value, isPacked)
        }

        /**
         * Phase 9 Trip Intelligence — generates one complete recommended
         * outfit per trip day (reusing the same [StylingEngineRepository]
         * every other screen's recommendations come from, never a second
         * suggestion mechanism), deduplicated across days so a garment
         * suggested for Day 1 isn't suggested again for Day 3, plus a
         * trip-length-scaled toiletries checklist and a small set of
         * optional reminder items. Each trip day's [SuggestionContext] has
         * no real weather (there is no forecast for a future or distant
         * destination — an honest, documented gap, not a fabricated one) and
         * no occasion bias from the trip's own free-text `activities` (this
         * schema has no `Occasion`/`DressCode` mapping for them) — the
         * suggestion still reflects the user's existing Stylist Preferences
         * and rotation/favorite scoring, exactly like any other recommendation.
         */
        override suspend fun generatePackingSuggestions(tripId: TripId): List<PackingListItem> {
            val trip = getTrip(tripId) ?: return emptyList()
            val days = tripDays(trip)
            val outfitItems = generateOutfitItems(stylingEngineRepository, tripId, days)
            return outfitItems + toiletriesChecklist(tripId, days.size) + reminderItems(tripId, trip)
        }
    }

// Top-level (not class members) purely to stay under detekt's `TooManyFunctions`
// ceiling on TripRepositoryImpl — the same "class stays a thin orchestrator"
// split used elsewhere in this package.

private fun tripDays(trip: Trip): List<LocalDate> =
    generateSequence(trip.dateRange.start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(trip.dateRange.end) }
        .toList()

private suspend fun generateOutfitItems(
    stylingEngineRepository: dagger.Lazy<StylingEngineRepository>,
    tripId: TripId,
    days: List<LocalDate>,
): List<PackingListItem> {
    val usedGarmentIds = mutableSetOf<Long>()
    val items = mutableListOf<PackingListItem>()
    days.forEachIndexed { index, day ->
        val context = SuggestionContext(date = day, weather = null, occasionId = null)
        val outfit =
            stylingEngineRepository
                .get()
                .suggestOutfits(context)
                .firstOrNull()
                ?.outfit
        outfit?.garments.orEmpty().forEach { slot ->
            if (usedGarmentIds.add(slot.garmentId.value)) {
                items +=
                    PackingListItem(
                        id = PackingListItemId(0),
                        tripId = tripId,
                        garmentId = slot.garmentId,
                        freeTextName = null,
                        category = "Day ${index + 1} Outfit",
                        isPacked = false,
                        rationale = "Suggested for Day ${index + 1} ($day)",
                    )
            }
        }
    }
    return items
}

private fun toiletriesChecklist(
    tripId: TripId,
    dayCount: Int,
): List<PackingListItem> {
    val baseItems = listOf("Toothbrush & toothpaste", "Skincare essentials", "Medication (if any)")
    val extendedItems =
        if (dayCount > LONG_TRIP_DAY_THRESHOLD) listOf("Extra toiletries for the longer stay") else emptyList()
    return (baseItems + extendedItems).map { name -> freeTextItem(tripId, name, "Toiletries") }
}

private fun reminderItems(
    tripId: TripId,
    trip: Trip,
): List<PackingListItem> {
    val items = mutableListOf("Phone charger", "Travel adapter (if needed)")
    if (trip.luggageSize == LuggageSize.CARRY_ON) items += "Check carry-on liquid restrictions"
    return items.map { name -> freeTextItem(tripId, name, "Reminders") }
}

private fun freeTextItem(
    tripId: TripId,
    name: String,
    category: String,
): PackingListItem =
    PackingListItem(
        id = PackingListItemId(0),
        tripId = tripId,
        garmentId = null,
        freeTextName = name,
        category = category,
        isPacked = false,
        rationale = null,
    )
