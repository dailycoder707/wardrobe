package com.wardrobe.app.feature.closet.fakes

import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ClosetPreferencesRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.MaterialRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.domain.repository.TagRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.MaterialId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImportQueueItem
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
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
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeGarmentRepository(
    initial: List<Garment> = emptyList(),
) : GarmentRepository {
    private val garmentsFlow = MutableStateFlow(initial)
    val favoritedIds = mutableSetOf<Long>()

    fun setGarments(garments: List<Garment>) {
        garmentsFlow.value = garments
    }

    /** Mirrors `GarmentRepositoryImpl`'s real contract: category/brand/status/
     * season/dressCode/isFavorite/searchQuery are the "SQL-level" fields;
     * color/material/tag/price are applied in-memory in the real
     * implementation (see `GarmentFilter`'s own doc comment) — this fake
     * replicates both halves so `ClosetViewModel` tests exercise the same
     * contract production code provides. */
    override fun observeGarments(filter: GarmentFilter): Flow<List<Garment>> =
        garmentsFlow.map { garments ->
            val searchQuery = filter.searchQuery
            val priceMin = filter.priceMin
            val priceMax = filter.priceMax
            garments.filter { garment ->
                (filter.status == null || garment.status == filter.status) &&
                    (filter.categoryId == null || garment.categoryId == filter.categoryId) &&
                    (filter.brandId == null || garment.brandId == filter.brandId) &&
                    (filter.season == null || filter.season in garment.seasons) &&
                    (filter.dressCode == null || filter.dressCode in garment.dressCodes) &&
                    (filter.isFavorite == null || garment.isFavorite == filter.isFavorite) &&
                    (
                        searchQuery == null ||
                            garment.name?.contains(searchQuery, ignoreCase = true) == true
                    ) &&
                    (filter.colorId == null || garment.palette.any { it.color.id == filter.colorId }) &&
                    (filter.materialId == null || garment.materials.any { it.material.id == filter.materialId }) &&
                    (filter.tagId == null || filter.tagId in garment.tagIds) &&
                    run {
                        val price = garment.price?.amount
                        (priceMin == null || (price != null && price >= priceMin)) &&
                            (priceMax == null || (price != null && price <= priceMax))
                    }
            }
        }

    override fun observeGarment(id: GarmentId): Flow<Garment?> =
        garmentsFlow.map { garments -> garments.firstOrNull { it.id == id } }

    override suspend fun getGarment(id: GarmentId): Garment? = garmentsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveGarment(garment: Garment): GarmentId {
        garmentsFlow.value = garmentsFlow.value + garment
        return garment.id
    }

    override suspend fun updateGarment(garment: Garment) {
        garmentsFlow.value = garmentsFlow.value.map { if (it.id == garment.id) garment else it }
    }

    override suspend fun setStatus(
        id: GarmentId,
        status: GarmentStatus,
    ) {
        garmentsFlow.value = garmentsFlow.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun setFavorite(
        id: GarmentId,
        isFavorite: Boolean,
    ) {
        if (isFavorite) favoritedIds.add(id.value) else favoritedIds.remove(id.value)
        garmentsFlow.value = garmentsFlow.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }

    override suspend fun setInLaundry(
        id: GarmentId,
        isInLaundry: Boolean,
    ) {
        garmentsFlow.value = garmentsFlow.value.map { if (it.id == id) it.copy(isInLaundry = isInLaundry) else it }
    }

    override suspend fun deleteGarment(id: GarmentId) {
        garmentsFlow.value = garmentsFlow.value.filterNot { it.id == id }
    }

    override suspend fun findPotentialDuplicates(
        categoryId: CategoryId,
        colorId: ColorId?,
        excludeGarmentId: GarmentId?,
    ): List<Garment> =
        garmentsFlow.value.filter { garment ->
            garment.categoryId == categoryId &&
                garment.status == GarmentStatus.ACTIVE &&
                (excludeGarmentId == null || garment.id != excludeGarmentId) &&
                (colorId == null || garment.primaryColorId == colorId)
        }
}

class FakeCategoryRepository(
    initial: List<Category> = emptyList(),
) : CategoryRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = flow.asStateFlow()

    override suspend fun create(
        name: String,
        parentId: CategoryId?,
    ): CategoryId = throw UnsupportedOperationException("not needed for tests")

    override suspend fun delete(id: CategoryId) = Unit
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

class FakeMaterialRepository(
    initial: List<Material> = emptyList(),
) : MaterialRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Material>> = flow.asStateFlow()

    override suspend fun create(name: String): MaterialId = throw UnsupportedOperationException("not needed for tests")
}

class FakeTagRepository(
    initial: List<Tag> = emptyList(),
) : TagRepository {
    private val flow = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Tag>> = flow.asStateFlow()

    override suspend fun create(name: String): TagId = throw UnsupportedOperationException("not needed for tests")

    override suspend fun delete(id: TagId) = Unit
}

class FakeStatsRepository(
    initial: List<CostPerWearEntry> = emptyList(),
) : StatsRepository {
    private val costPerWearFlow = MutableStateFlow(initial)

    fun setCostPerWear(entries: List<CostPerWearEntry>) {
        costPerWearFlow.value = entries
    }

    override fun observeUsageStats(window: StatsWindow): Flow<UsageStats> =
        costPerWearFlow.map { entries ->
            UsageStats(
                window = window,
                totalActiveGarments = entries.size,
                wornAtLeastOnce = entries.count { it.totalWearCount > 0 },
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
        }

    override fun observeCostPerWear(): Flow<List<CostPerWearEntry>> = costPerWearFlow.asStateFlow()

    override fun observeDormantItems(window: StatsWindow): Flow<List<DormantItem>> = flowOf(emptyList())

    override fun observeClosetGaps(): Flow<List<ClosetGap>> = flowOf(emptyList())

    override fun observeWearHeatmap(range: DateRange): Flow<List<WearHeatmapDay>> = flowOf(emptyList())

    override fun observeRepeatedOutfits(
        window: StatsWindow,
        limit: Int,
    ): Flow<List<RepeatedOutfit>> = flowOf(emptyList())

    override fun observeNeverWornOutfitIds(): Flow<List<OutfitId>> = flowOf(emptyList())

    override fun observeGarmentsMissingOutfits(): Flow<List<GarmentId>> = flowOf(emptyList())

    override fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int> = flowOf(0)

    override fun observeTopDressCode(
        window: StatsWindow,
        isWeekend: Boolean,
    ): Flow<DressCode?> = flowOf(null)
}

class FakeImportQueueRepository : ImportQueueRepository {
    private val mutableQueue = MutableStateFlow<List<ImportQueueItem>>(emptyList())
    private var nextId = 1L

    override suspend fun enqueue(filePaths: List<String>): List<ImportQueueItem> {
        val now = java.time.Instant.now()
        val items =
            filePaths.map { path ->
                ImportQueueItem(
                    id = nextId++,
                    sourceFilePath = path,
                    stagingId = null,
                    status = ImportQueueItemStatus.PENDING,
                    errorMessage = null,
                    savedGarmentId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        mutableQueue.value = mutableQueue.value + items
        return items
    }

    override fun observeQueue(): Flow<List<ImportQueueItem>> = mutableQueue.asStateFlow()

    override fun observeIncompleteCount(): Flow<Int> =
        mutableQueue.map { items -> items.count { it.status != ImportQueueItemStatus.COMPLETED } }

    override suspend fun updateItem(item: ImportQueueItem) {
        mutableQueue.value = mutableQueue.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun deleteCompleted() {
        mutableQueue.value = mutableQueue.value.filterNot { it.status == ImportQueueItemStatus.COMPLETED }
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

    override suspend fun clearDay(date: LocalDate) {
        flow.value = flow.value.filterNot { it.date == date }
    }

    override suspend fun duplicateDay(
        from: LocalDate,
        to: LocalDate,
    ) {
        val copies =
            flow.value
                .filter { it.date == from }
                .map { it.copy(id = WearEventId(0), date = to, status = WearEventStatus.PLANNED) }
        flow.value = flow.value + copies
    }
}

class FakePersonalizationRepository(
    initial: PersonalizationSettings = PersonalizationSettings.DEFAULT,
) : PersonalizationRepository {
    private val flow = MutableStateFlow(initial)

    fun set(settings: PersonalizationSettings) {
        flow.value = settings
    }

    override fun observe(): Flow<PersonalizationSettings> = flow.asStateFlow()

    override suspend fun setDisplayName(name: String?) {
        flow.value = flow.value.copy(displayName = name)
    }

    override suspend fun setGreetingStyle(style: com.wardrobe.app.core.model.profile.GreetingStyle) {
        flow.value = flow.value.copy(greetingStyle = style)
    }

    override suspend fun setCustomHomeTitle(title: String?) {
        flow.value = flow.value.copy(customHomeTitle = title)
    }

    override suspend fun setAvatarImageUri(uri: String?) {
        flow.value = flow.value.copy(avatarImageUri = uri)
    }

    override suspend fun setShowGreeting(show: Boolean) {
        flow.value = flow.value.copy(showGreeting = show)
    }

    override suspend fun setShowWeatherCard(show: Boolean) {
        flow.value = flow.value.copy(showWeatherCard = show)
    }

    override suspend fun setShowRecommendationCard(show: Boolean) {
        flow.value = flow.value.copy(showRecommendationCard = show)
    }

    override suspend fun setShowWardrobeHealthCard(show: Boolean) {
        flow.value = flow.value.copy(showWardrobeHealthCard = show)
    }

    override suspend fun setShowInspirationCard(show: Boolean) {
        flow.value = flow.value.copy(showInspirationCard = show)
    }
}

class FakeClosetPreferencesRepository : ClosetPreferencesRepository {
    private val sortFlow = MutableStateFlow(GarmentSort.DEFAULT)
    private val gridColumnCountFlow = MutableStateFlow(3)
    private val recentSearchesFlow = MutableStateFlow<List<String>>(emptyList())

    override fun observeSort(): Flow<GarmentSort> = sortFlow.asStateFlow()

    override suspend fun setSort(sort: GarmentSort) {
        sortFlow.value = sort
    }

    override fun observeGridColumnCount(): Flow<Int> = gridColumnCountFlow.asStateFlow()

    override suspend fun setGridColumnCount(count: Int) {
        gridColumnCountFlow.value = count
    }

    override fun observeRecentSearches(): Flow<List<String>> = recentSearchesFlow.asStateFlow()

    override suspend fun addRecentSearch(query: String) {
        recentSearchesFlow.value = (listOf(query) + recentSearchesFlow.value).distinct()
    }

    override suspend fun clearRecentSearches() {
        recentSearchesFlow.value = emptyList()
    }
}

class FakeWeatherRepository(
    var snapshot: WeatherSnapshot? = null,
) : WeatherRepository {
    override suspend fun getForecast(
        location: Location,
        date: LocalDate,
    ): WeatherSnapshot = snapshot ?: error("FakeWeatherRepository.getForecast called with no snapshot configured")

    override suspend fun getForecastForConfiguredLocation(date: LocalDate): WeatherSnapshot? = snapshot
}

class FakeStylingEngineRepository(
    var suggestions: List<ScoredOutfit> = emptyList(),
) : StylingEngineRepository {
    override suspend fun suggestOutfits(context: SuggestionContext): List<ScoredOutfit> = suggestions

    override suspend fun suggestForItem(
        garmentId: GarmentId,
        context: SuggestionContext,
    ): List<ScoredOutfit> = suggestions

    override suspend fun suggestReplacementForSlot(
        outfit: Outfit,
        slot: OutfitSlot,
        context: SuggestionContext,
    ): GarmentId? = null

    override suspend fun suggestReplacementForAccessory(
        outfit: Outfit,
        category: AccessoryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId? = null

    override suspend fun suggestReplacementForJewelry(
        outfit: Outfit,
        category: JewelryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId? = null

    override fun lastRunDiagnostics(): RecommendationRunDiagnostics = RecommendationRunDiagnostics()
}

class FakeTripRepository(
    initial: List<Trip> = emptyList(),
) : TripRepository {
    private val tripsFlow = MutableStateFlow(initial)
    private val packingListsByTrip = mutableMapOf<Long, MutableStateFlow<List<PackingListItem>>>()

    override fun observeTrips(): Flow<List<Trip>> = tripsFlow.asStateFlow()

    override suspend fun getTrip(id: TripId): Trip? = tripsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveTrip(trip: Trip): TripId {
        tripsFlow.value = tripsFlow.value + trip
        return trip.id
    }

    override suspend fun deleteTrip(id: TripId) {
        tripsFlow.value = tripsFlow.value.filterNot { it.id == id }
    }

    override fun observePackingList(tripId: TripId): Flow<List<PackingListItem>> =
        packingListsByTrip.getOrPut(tripId.value) { MutableStateFlow(emptyList()) }.asStateFlow()

    override suspend fun savePackingList(
        tripId: TripId,
        items: List<PackingListItem>,
    ) {
        packingListsByTrip.getOrPut(tripId.value) { MutableStateFlow(emptyList()) }.value = items
    }

    override suspend fun setPacked(
        itemId: PackingListItemId,
        isPacked: Boolean,
    ) {
        packingListsByTrip.values.forEach { flow ->
            flow.value = flow.value.map { if (it.id == itemId) it.copy(isPacked = isPacked) else it }
        }
    }

    override suspend fun generatePackingSuggestions(tripId: TripId): List<PackingListItem> = emptyList()
}

class FakeWardrobeIntelligenceRepository : WardrobeIntelligenceRepository {
    var dailyBrief: DailyBrief =
        DailyBrief(
            greeting = "",
            weather = null,
            todaysOccasionName = null,
            recommendation = null,
            explanations = emptyList(),
        )
    var healthScore: WardrobeHealthScore =
        WardrobeHealthScore(score = 0, rotationScore = 0, usagePercent = 0, forgottenCount = 0)

    override fun observeGarmentInsights(garmentId: GarmentId): Flow<GarmentInsights?> = flowOf(null)

    override fun observeOutfitInsights(outfitId: OutfitId): Flow<OutfitInsights?> = flowOf(null)

    override fun observeWardrobeAlerts(): Flow<WardrobeAlerts> =
        flowOf(WardrobeAlerts(forgotten = emptyList(), overused = emptyList(), neverWorn = emptyList()))

    override fun observeShoppingGaps(): Flow<List<ShoppingGapSuggestion>> = flowOf(emptyList())

    override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> = flowOf(emptyList())

    override suspend fun suggestCapsule(type: CapsuleType): CapsuleSuggestion = CapsuleSuggestion(type, emptyMap(), "")

    override fun observeWardrobeHealthScore(): Flow<WardrobeHealthScore> = flowOf(healthScore)

    override fun observeCalendarConflicts(lookAheadDays: Int): Flow<List<CalendarConflict>> = flowOf(emptyList())

    override suspend fun buildDailyBrief(
        today: LocalDate,
        greeting: String,
    ): DailyBrief = dailyBrief.copy(greeting = greeting)
}

class FakeSyncRepository : SyncRepository {
    private val statusFlow = MutableStateFlow(SyncStatusSnapshot())
    private val conflictsFlow = MutableStateFlow<List<SyncConflict>>(emptyList())
    private val historyFlow = MutableStateFlow<List<SyncHistoryEntry>>(emptyList())

    override fun observeStatus(): Flow<SyncStatusSnapshot> = statusFlow.asStateFlow()

    override suspend fun syncNow() = Unit

    override fun observeUnresolvedConflicts(): Flow<List<SyncConflict>> = conflictsFlow.asStateFlow()

    override suspend fun resolveConflict(
        conflictId: Long,
        resolution: ConflictResolution,
    ) = Unit

    override fun observeHistory(limit: Int): Flow<List<SyncHistoryEntry>> = historyFlow.asStateFlow()
}
