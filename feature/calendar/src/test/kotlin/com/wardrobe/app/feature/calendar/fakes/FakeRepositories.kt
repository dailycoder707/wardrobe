package com.wardrobe.app.feature.calendar.fakes

import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.DressCode
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
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.stats.ClosetGap
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.stats.WardrobeMixDistribution
import com.wardrobe.app.core.model.stats.WardrobeUsageGaps
import com.wardrobe.app.core.model.stats.WearHeatmapDay
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
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

    /** M22 regression test hook: simulates a real repository-flow failure so
     * `CalendarViewModel.uiState`'s error boundary can be proven, rather
     * than asserted from reading the code alone. */
    var observeEventsError: Throwable? = null

    override fun observeEvents(range: DateRange): Flow<List<WearEvent>> =
        flow.map { events ->
            observeEventsError?.let { throw it }
            events.filter { !it.date.isBefore(range.start) && !it.date.isAfter(range.end) }
        }

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

/** M20 — always returns [suggestions] verbatim regardless of [SuggestionContext],
 * mirroring `feature:outfits`' own `FakeStylingEngineRepository` (M19)
 * exactly: `CalendarViewModelTest` only needs to verify it wires the
 * engine's output into Day Detail's recommendation state correctly, not
 * re-verify the rule engine itself. */
class FakeStylingEngineRepository(
    var suggestions: List<ScoredOutfit> = emptyList(),
) : StylingEngineRepository {
    var lastRequestedCount: Int = 0
    var lastContext: SuggestionContext? = null
    var suggestionsForCount: ((Int) -> List<ScoredOutfit>)? = null
    var errorToThrow: Throwable? = null

    override suspend fun suggestOutfits(
        context: SuggestionContext,
        count: Int,
    ): List<ScoredOutfit> {
        errorToThrow?.let { throw it }
        lastRequestedCount = count
        lastContext = context
        return suggestionsForCount?.invoke(count) ?: suggestions
    }

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

class FakeWeatherRepository(
    var snapshot: WeatherSnapshot? = null,
) : WeatherRepository {
    override suspend fun getForecast(
        location: Location,
        date: LocalDate,
    ): WeatherSnapshot = snapshot ?: error("FakeWeatherRepository.getForecast called with no snapshot configured")

    override suspend fun getForecastForConfiguredLocation(date: LocalDate): WeatherSnapshot? = snapshot
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

    override fun observeWardrobeUsageGaps(): Flow<WardrobeUsageGaps> =
        flowOf(WardrobeUsageGaps(emptyList(), emptyList()))

    override fun observeOutfitWearEventCount(window: StatsWindow): Flow<Int> = flowOf(0)

    override fun observeTopDressCode(
        window: StatsWindow,
        isWeekend: Boolean,
    ): Flow<DressCode?> = flowOf(null)

    override fun observeWardrobeMixDistribution(): Flow<WardrobeMixDistribution> =
        flowOf(WardrobeMixDistribution(emptyList(), emptyList(), emptyList(), 0))
}
