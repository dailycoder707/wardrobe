package com.wardrobe.app.feature.trips.fakes

import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Minimal, module-local fakes for `feature:trips` ViewModel tests — the
 * same "real state in a `MutableStateFlow`, no mocking framework needed for
 * a straightforward CRUD-shaped repository" pattern every other feature
 * module's fakes file already uses. */
class FakeTripRepository(
    initialTrips: List<Trip> = emptyList(),
) : TripRepository {
    private val trips = MutableStateFlow(initialTrips)
    private val packingLists = MutableStateFlow<Map<TripId, List<PackingListItem>>>(emptyMap())
    var generatedSuggestions: List<PackingListItem> = emptyList()
    var deletedTripId: TripId? = null

    override fun observeTrips(): Flow<List<Trip>> = trips.map { it.sortedBy { trip -> trip.dateRange.start } }

    override suspend fun getTrip(id: TripId): Trip? = trips.value.firstOrNull { it.id == id }

    override suspend fun saveTrip(trip: Trip): TripId {
        val id = if (trip.id.value == 0L) TripId(trips.value.size + 1L) else trip.id
        trips.value = trips.value.filterNot { it.id == id } + trip.copy(id = id)
        return id
    }

    override suspend fun deleteTrip(id: TripId) {
        deletedTripId = id
        trips.value = trips.value.filterNot { it.id == id }
    }

    override fun observePackingList(tripId: TripId): Flow<List<PackingListItem>> =
        packingLists.map { it[tripId].orEmpty() }

    override suspend fun savePackingList(
        tripId: TripId,
        items: List<PackingListItem>,
    ) {
        packingLists.value = packingLists.value + (tripId to items)
    }

    override suspend fun setPacked(
        itemId: PackingListItemId,
        isPacked: Boolean,
    ) {
        packingLists.value =
            packingLists.value.mapValues { (_, items) ->
                items.map { if (it.id == itemId) it.copy(isPacked = isPacked) else it }
            }
    }

    override suspend fun generatePackingSuggestions(tripId: TripId): List<PackingListItem> = generatedSuggestions
}

class FakeGarmentRepository(
    initial: List<Garment> = emptyList(),
) : GarmentRepository {
    private val garments = MutableStateFlow(initial)

    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> =
        garments.map { list -> list.filter { filter.status == null || it.status == filter.status } }

    override fun observeGarment(id: GarmentId): Flow<Garment?> =
        garments.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getGarment(id: GarmentId): Garment? = garments.value.firstOrNull { it.id == id }

    override suspend fun saveGarment(garment: Garment): GarmentId {
        garments.value = garments.value + garment
        return garment.id
    }

    override suspend fun updateGarment(garment: Garment) {
        garments.value = garments.value.map { if (it.id == garment.id) garment else it }
    }

    override suspend fun setStatus(
        id: GarmentId,
        status: GarmentStatus,
    ) {
        garments.value = garments.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun setFavorite(
        id: GarmentId,
        isFavorite: Boolean,
    ) {
        garments.value = garments.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setInLaundry(
        id: GarmentId,
        isInLaundry: Boolean,
    ) {
        garments.value = garments.value.map { if (it.id == id) it.copy(isInLaundry = isInLaundry) else it }
    }

    override suspend fun deleteGarment(id: GarmentId) {
        garments.value = garments.value.filterNot { it.id == id }
    }

    override suspend fun findPotentialDuplicates(
        categoryId: com.wardrobe.app.core.model.common.CategoryId,
        colorId: com.wardrobe.app.core.model.common.ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment> =
        garments.value.filter { garment ->
            garment.categoryId == categoryId &&
                garment.status == GarmentStatus.ACTIVE &&
                (excludeGarmentId == null || garment.id != excludeGarmentId) &&
                (colorId == null || garment.primaryColorId == colorId)
        }
}
