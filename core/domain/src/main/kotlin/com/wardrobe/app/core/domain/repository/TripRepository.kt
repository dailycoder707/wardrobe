package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>

    suspend fun getTrip(id: TripId): Trip?

    suspend fun saveTrip(trip: Trip): TripId

    suspend fun deleteTrip(id: TripId)

    fun observePackingList(tripId: TripId): Flow<List<PackingListItem>>

    /** Replaces the packing list wholesale — the rule-based generation logic (Phase 6)
     * decides the full list at once from weather + activities, rather than this
     * repository merging incremental changes. */
    suspend fun savePackingList(
        tripId: TripId,
        items: List<PackingListItem>,
    )

    suspend fun setPacked(
        itemId: PackingListItemId,
        isPacked: Boolean,
    )

    /** Phase 9 Trip Intelligence — generates a real packing list (per-day
     * outfits, a toiletries checklist, optional reminder items) from the
     * trip's own duration/activities/luggage size. Pure generation: the
     * caller decides whether to [savePackingList] the result, exactly the
     * "the rule-based generation logic decides the full list at once"
     * contract [savePackingList]'s own KDoc already described (Phase 6's
     * comment, never actually implemented until now). */
    suspend fun generatePackingSuggestions(tripId: TripId): List<PackingListItem>
}
