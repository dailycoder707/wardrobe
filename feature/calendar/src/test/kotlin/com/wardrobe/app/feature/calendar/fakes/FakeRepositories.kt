package com.wardrobe.app.feature.calendar.fakes

import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.intelligence.CapsuleSuggestion
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.model.intelligence.DailyBrief
import com.wardrobe.app.core.model.intelligence.DuplicateGroup
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.ShoppingGapSuggestion
import com.wardrobe.app.core.model.intelligence.WardrobeAlerts
import com.wardrobe.app.core.model.intelligence.WardrobeHealthScore
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeGarmentRepository(
    initial: List<Garment> = emptyList(),
) : GarmentRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> =
        flow.map { garments -> garments.filter { filter.status == null || it.status == filter.status } }

    override fun observeGarment(id: GarmentId): Flow<Garment?> =
        flow.map { garments ->
            garments.firstOrNull {
                it.id ==
                    id
            }
        }

    override suspend fun getGarment(id: GarmentId): Garment? = flow.value.firstOrNull { it.id == id }

    override suspend fun saveGarment(garment: Garment): GarmentId {
        flow.value = flow.value + garment
        return garment.id
    }

    override suspend fun updateGarment(garment: Garment) {
        flow.value = flow.value.map { if (it.id == garment.id) garment else it }
    }

    override suspend fun setStatus(
        id: GarmentId,
        status: GarmentStatus,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun setFavorite(
        id: GarmentId,
        isFavorite: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setInLaundry(
        id: GarmentId,
        isInLaundry: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isInLaundry = isInLaundry) else it }
    }

    override suspend fun deleteGarment(id: GarmentId) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun findPotentialDuplicates(
        categoryId: CategoryId,
        colorId: com.wardrobe.app.core.model.common.ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment> =
        flow.value.filter { garment ->
            garment.categoryId == categoryId &&
                garment.status == GarmentStatus.ACTIVE &&
                (excludeGarmentId == null || garment.id != excludeGarmentId) &&
                (colorId == null || garment.primaryColorId == colorId)
        }
}

class FakeOutfitRepository(
    initial: List<Outfit> = emptyList(),
) : OutfitRepository {
    private val flow = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1

    override fun observeOutfits(filter: OutfitFilter): Flow<List<Outfit>> =
        flow.map { outfits ->
            outfits.filter { outfit ->
                (filter.isSaved == null || outfit.isSaved == filter.isSaved) &&
                    (filter.isArchived == null || outfit.isArchived == filter.isArchived)
            }
        }

    override fun observeOutfit(id: OutfitId): Flow<Outfit?> =
        flow.map { outfits -> outfits.firstOrNull { it.id == id } }

    override suspend fun getOutfit(id: OutfitId): Outfit? = flow.value.firstOrNull { it.id == id }

    override suspend fun saveOutfit(outfit: Outfit): OutfitId {
        if (outfit.id.value == 0L) {
            val newOutfit = outfit.copy(id = OutfitId(nextId++))
            flow.value = flow.value + newOutfit
            return newOutfit.id
        }
        flow.value = flow.value.map { if (it.id == outfit.id) outfit else it }
        return outfit.id
    }

    override suspend fun setFavorite(
        id: OutfitId,
        isFavorite: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setArchived(
        id: OutfitId,
        isArchived: Boolean,
    ) {
        flow.value = flow.value.map { if (it.id == id) it.copy(isArchived = isArchived) else it }
    }

    override suspend fun deleteOutfit(id: OutfitId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}

class FakeWearEventRepository(
    initial: List<WearEvent> = emptyList(),
) : WearEventRepository {
    private val flow = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id.value } ?: 0L) + 1

    override fun observeEvents(range: DateRange): Flow<List<WearEvent>> =
        flow.map { events -> events.filter { !it.date.isBefore(range.start) && !it.date.isAfter(range.end) } }

    override suspend fun logWear(event: WearEvent): WearEventId {
        val stored = if (event.id.value == 0L) event.copy(id = WearEventId(nextId++)) else event
        flow.value = flow.value + stored
        return stored.id
    }

    override suspend fun updateWear(event: WearEvent) {
        flow.value = flow.value.map { if (it.id == event.id) event else it }
    }

    override suspend fun deleteEvent(id: WearEventId) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun clearDay(date: java.time.LocalDate) {
        flow.value = flow.value.filterNot { it.date == date }
    }

    override suspend fun duplicateDay(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
    ) {
        val copies =
            flow.value.filter { it.date == from }.map {
                it.copy(id = WearEventId(nextId++), date = to, status = WearEventStatus.PLANNED)
            }
        flow.value = flow.value + copies
    }
}

class FakeOccasionRepository(
    initial: List<Occasion> = emptyList(),
) : OccasionRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Occasion>> = flow.asStateFlow()

    override suspend fun create(name: String): OccasionId = OccasionId(0)
}

class FakeWardrobeIntelligenceRepository : WardrobeIntelligenceRepository {
    val conflicts = MutableStateFlow<List<CalendarConflict>>(emptyList())

    override fun observeGarmentInsights(garmentId: GarmentId): Flow<GarmentInsights?> = flowOf(null)

    override fun observeOutfitInsights(outfitId: OutfitId): Flow<OutfitInsights?> = flowOf(null)

    override fun observeWardrobeAlerts(): Flow<WardrobeAlerts> =
        flowOf(WardrobeAlerts(forgotten = emptyList(), overused = emptyList(), neverWorn = emptyList()))

    override fun observeShoppingGaps(): Flow<List<ShoppingGapSuggestion>> = flowOf(emptyList())

    override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())

    override suspend fun suggestCapsule(type: CapsuleType): CapsuleSuggestion = CapsuleSuggestion(type, emptyMap(), "")

    override fun observeWardrobeHealthScore(): Flow<WardrobeHealthScore> =
        flowOf(WardrobeHealthScore(score = 0, rotationScore = 0, usagePercent = 0, forgottenCount = 0))

    override fun observeCalendarConflicts(lookAheadDays: Int): Flow<List<CalendarConflict>> = conflicts.asStateFlow()

    override suspend fun buildDailyBrief(
        today: LocalDate,
        greeting: String,
    ): DailyBrief = DailyBrief(greeting, null, null, null, emptyList())
}
