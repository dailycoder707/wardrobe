package com.wardrobe.app.feature.stats.fakes

import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.wear.WearEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeGarmentRepository(
    initial: List<Garment> = emptyList(),
) : GarmentRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> =
        flow.map { garments -> garments.filter { filter.status == null || it.status == filter.status } }

    override fun observeGarment(id: GarmentId): Flow<Garment?> =
        flow.map { garments -> garments.firstOrNull { it.id == id } }

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
        status: com.wardrobe.app.core.model.garment.GarmentStatus,
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
        categoryId: com.wardrobe.app.core.model.common.CategoryId,
        colorId: ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment> =
        flow.value.filter { garment ->
            garment.categoryId == categoryId &&
                garment.status == com.wardrobe.app.core.model.garment.GarmentStatus.ACTIVE &&
                (excludeGarmentId == null || garment.id != excludeGarmentId) &&
                (colorId == null || garment.primaryColorId == colorId)
        }
}

class FakeOutfitRepository(
    initial: List<Outfit> = emptyList(),
) : OutfitRepository {
    private val flow = MutableStateFlow(initial)

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
        flow.value = flow.value + outfit
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

class FakeCategoryRepository(
    initial: List<Category> = emptyList(),
) : CategoryRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        parentId: com.wardrobe.app.core.model.common.CategoryId?,
    ) = throw UnsupportedOperationException("not needed for tests")

    override suspend fun delete(id: com.wardrobe.app.core.model.common.CategoryId) = Unit
}

class FakeBrandRepository(
    initial: List<Brand> = emptyList(),
) : BrandRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Brand>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        logoUri: String?,
    ): BrandId = throw UnsupportedOperationException("not needed for tests")
}

class FakeColorRepository(
    initial: List<Color> = emptyList(),
) : ColorRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Color>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        hexValue: String,
    ): ColorId = throw UnsupportedOperationException("not needed for tests")
}

class FakeWearEventRepository(
    initial: List<WearEvent> = emptyList(),
) : WearEventRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeEvents(range: DateRange): Flow<List<WearEvent>> =
        flow.map { events -> events.filter { !it.date.isBefore(range.start) && !it.date.isAfter(range.end) } }

    override suspend fun logWear(event: WearEvent): WearEventId {
        flow.value = flow.value + event
        return event.id
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
    ) = Unit
}

/** Every method returns a settable [MutableStateFlow] so tests can push new
 * values mid-collection — `StatsRepository`'s real methods are all `Flow`,
 * never `suspend`, and this fake mirrors that exactly. */
class FakeStatsRepository : StatsRepository {
    val usageStats = MutableStateFlow(emptyUsageStats(StatsWindow.ONE_YEAR))
    val costPerWear = MutableStateFlow<List<CostPerWearEntry>>(emptyList())
    val dormantItems = MutableStateFlow<List<DormantItem>>(emptyList())
    val closetGaps = MutableStateFlow<List<ClosetGap>>(emptyList())
    val wearHeatmap = MutableStateFlow<List<WearHeatmapDay>>(emptyList())
    val repeatedOutfits = MutableStateFlow<List<RepeatedOutfit>>(emptyList())
    val neverWornOutfitIds = MutableStateFlow<List<OutfitId>>(emptyList())
    val garmentsMissingOutfits = MutableStateFlow<List<GarmentId>>(emptyList())
    val outfitWearEventCount = MutableStateFlow(0)
    val topWeekendDressCode = MutableStateFlow<DressCode?>(null)
    val topWeekdayDressCode = MutableStateFlow<DressCode?>(null)

    /** Mirrors `StatsRepositoryImpl`'s real contract: the emitted [UsageStats]
     * always reflects the actually-requested [window], not just whatever
     * [usageStats] happened to be seeded with — a ViewModel test that toggles
     * windows needs to see that reflected, the same as production. */
    override fun observeUsageStats(window: StatsWindow): Flow<UsageStats> = usageStats.map { it.copy(window = window) }

    override fun observeCostPerWear(): Flow<List<CostPerWearEntry>> = costPerWear.asStateFlow()

    override fun observeDormantItems(window: StatsWindow): Flow<List<DormantItem>> = dormantItems.asStateFlow()

    override fun observeClosetGaps(): Flow<List<ClosetGap>> = closetGaps.asStateFlow()

    override fun observeWearHeatmap(range: DateRange): Flow<List<WearHeatmapDay>> = wearHeatmap.asStateFlow()

    override fun observeRepeatedOutfits(
        window: StatsWindow,
        limit: Int,
    ): Flow<List<RepeatedOutfit>> = repeatedOutfits.asStateFlow()

    override fun observeNeverWornOutfitIds(): Flow<List<OutfitId>> = neverWornOutfitIds.asStateFlow()

    override fun observeGarmentsMissingOutfits(): Flow<List<GarmentId>> = garmentsMissingOutfits.asStateFlow()

    override fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int> = outfitWearEventCount.asStateFlow()

    override fun observeTopDressCode(
        window: StatsWindow,
        isWeekend: Boolean,
    ): Flow<DressCode?> = if (isWeekend) topWeekendDressCode.asStateFlow() else topWeekdayDressCode.asStateFlow()
}

fun emptyUsageStats(window: StatsWindow): UsageStats =
    UsageStats(
        window = window,
        totalActiveGarments = 0,
        wornAtLeastOnce = 0,
        usagePercent = 0.0,
        mostWornGarmentIds = emptyList(),
        leastWornGarmentIds = emptyList(),
        favouriteBrandIds = emptyList(),
        signatureColorIds = emptyList(),
        favouriteCategoryIds = emptyList(),
        wearsBySeason = emptyMap(),
        wearsByDressCode = emptyMap(),
        weekdayWearCount = 0,
        weekendWearCount = 0,
    )
