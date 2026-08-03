package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.repository.weather.WeatherLocationResolver
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.Location
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherPreferencesRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.StyleRule
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.styling.WeatherSource
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.core.model.trip.Trip
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val TODAY = LocalDate.of(2026, 8, 2)
private val FIXED_CLOCK =
    Clock.fixed(
        TODAY.atStartOfDay(ZoneOffset.UTC).toInstant().plus(9, ChronoUnit.HOURS),
        ZoneOffset.UTC,
    )
private val TOPS_CATEGORY = Category(CategoryId(1), "Tops", null, CategoryLevel.TOP)
private val BOTTOMS_CATEGORY = Category(CategoryId(2), "Bottoms", null, CategoryLevel.TOP)

private fun testGarment(
    id: Long,
    categoryId: Long,
) = Garment(
    id = GarmentId(id),
    name = "Garment $id",
    categoryId = CategoryId(categoryId),
    primaryColorId = null,
    palette = emptyList(),
    materials = emptyList(),
    tagIds = emptyList(),
    seasons = emptySet(),
    dressCodes = emptySet(),
    pattern = null,
    fit = null,
    length = null,
    sleeveLength = null,
    warmthRating = null,
    breathabilityRating = null,
    brandId = BrandId(1),
    size = null,
    price = null,
    purchaseDate = null,
    condition = null,
    careNotes = null,
    status = GarmentStatus.ACTIVE,
    isReviewed = true,
    isFavorite = false,
    images = emptyList(),
    createdAt = Instant.parse("2020-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
)

/**
 * "Recommendation refinement tests" / "Calendar context tests" / "Trip
 * exclusion tests" (Phase 7 brief) at the repository level — the pure
 * scoring-function tests already live in `RecommendationRuleEngineTest`; this
 * class covers behavior that only exists inside `StylingEngineRepositoryImpl`
 * itself (planned-outfit surfacing, whole-recommendation context notes,
 * diagnostics).
 */
class StylingEngineRepositoryImplTest {
    private val garmentRepository = mockk<GarmentRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val colorRepository = mockk<ColorRepository>(relaxed = true)
    private val styleRuleRepository = mockk<StyleRuleRepository>(relaxed = true)
    private val stylistPreferencesRepository = mockk<StylistPreferencesRepository>()
    private val tripRepository = mockk<TripRepository>()
    private val statsRepository = mockk<StatsRepository>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>()
    private val wearEventRepository = mockk<WearEventRepository>()
    private val outfitRepository = mockk<OutfitRepository>()
    private val occasionRepository = mockk<OccasionRepository>(relaxed = true)

    private fun repository(): StylingEngineRepositoryImpl {
        every { categoryRepository.observeAll() } returns flowOf(listOf(TOPS_CATEGORY, BOTTOMS_CATEGORY))
        every { colorRepository.observeAll() } returns flowOf(emptyList())
        every { statsRepository.observeCostPerWear() } returns flowOf(emptyList<CostPerWearEntry>())
        every { statsRepository.observeUsageStats(StatsWindow.SIX_MONTHS) } returns
            flowOf(
                UsageStats(
                    window = StatsWindow.SIX_MONTHS,
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
                ),
            )
        every { stylistPreferencesRepository.observePreferences() } returns flowOf(RecommendationPreferences())
        every { styleRuleRepository.observeActiveRules() } returns flowOf(emptyList<StyleRule>())
        coEvery { weatherRepository.getForecastForConfiguredLocation(any()) } returns null
        every { tripRepository.observeTrips() } returns flowOf(emptyList<Trip>())

        val contextDependencies =
            StylingContextDependencies(weatherRepository, wearEventRepository, outfitRepository, occasionRepository)
        return StylingEngineRepositoryImpl(
            garmentRepository,
            categoryRepository,
            colorRepository,
            styleRuleRepository,
            stylistPreferencesRepository,
            tripRepository,
            statsRepository,
            contextDependencies,
            FIXED_CLOCK,
        )
    }

    private fun stubGarments(garments: List<Garment>) {
        every { garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)) } returns
            flowOf(garments)
    }

    private fun stubNoPlannedEvents() {
        every { wearEventRepository.observeEvents(DateRange(TODAY, TODAY)) } returns flowOf(emptyList<WearEvent>())
    }

    @Test
    fun `suggestOutfits surfaces an already-planned outfit first with a fixed explanation`() =
        runTest {
            stubGarments(listOf(testGarment(1, 1), testGarment(2, 2)))
            val plannedOutfit =
                Outfit(
                    id = OutfitId(5),
                    name = "Planned Look",
                    garments = listOf(OutfitGarmentSlot(GarmentId(1), OutfitSlot.TOP.slotIndex)),
                    occasionId = null,
                    source = OutfitSource.USER_CREATED,
                    isSaved = true,
                    photoUri = null,
                    createdAt = Instant.now(FIXED_CLOCK),
                )
            every { wearEventRepository.observeEvents(DateRange(TODAY, TODAY)) } returns
                flowOf(
                    listOf(
                        WearEvent(
                            id = WearEventId(1),
                            date = TODAY,
                            garmentId = null,
                            outfitId = OutfitId(5),
                            weatherCacheId = null,
                            occasionId = null,
                            note = null,
                            status = WearEventStatus.PLANNED,
                            createdAt = Instant.now(FIXED_CLOCK),
                        ),
                    ),
                )
            coEvery { outfitRepository.getOutfit(OutfitId(5)) } returns plannedOutfit

            val engine = repository()
            val results = engine.suggestOutfits(SuggestionContext(date = TODAY, weather = null, occasionId = null))

            assertEquals(plannedOutfit, results.first().outfit)
            assertEquals("You already planned this outfit for today.", results.first().explanation)
            assertTrue(engine.lastRunDiagnostics().plannedOutfitUsed)
        }

    @Test
    fun `suggestOutfits adds a limited-choices note when most garments are packed for an active trip`() =
        runTest {
            val packedGarment = testGarment(1, 1)
            val availableGarment = testGarment(2, 2)
            stubGarments(listOf(packedGarment, availableGarment))
            stubNoPlannedEvents()

            // Built before the trip-specific stubs below: `repository()`'s own default
            // `tripRepository.observeTrips()` stub would otherwise clobber these
            // (MockK's last `every` for a given call signature wins).
            val engine = repository()
            val trip =
                Trip(
                    id =
                        com.wardrobe.app.core.model.common
                            .TripId(1),
                    name = "Trip",
                    destination = "Paris",
                    dateRange = DateRange(TODAY.minusDays(1), TODAY.plusDays(3)),
                    activities = emptyList(),
                    luggageSize = null,
                    createdAt = Instant.now(FIXED_CLOCK),
                )
            every { tripRepository.observeTrips() } returns flowOf(listOf(trip))
            every { tripRepository.observePackingList(trip.id) } returns
                flowOf(
                    listOf(
                        PackingListItem(
                            id =
                                com.wardrobe.app.core.model.common
                                    .PackingListItemId(1),
                            tripId = trip.id,
                            garmentId = GarmentId(1),
                            freeTextName = null,
                            category = null,
                            isPacked = true,
                            rationale = null,
                        ),
                    ),
                )

            engine.suggestOutfits(SuggestionContext(date = TODAY, weather = null, occasionId = null))

            val diagnostics = engine.lastRunDiagnostics()
            assertTrue(diagnostics.contextNotes.any { it.contains("packed", ignoreCase = true) })
        }

    @Test
    fun `lastRunDiagnostics reports NONE weather source when weather is unavailable`() =
        runTest {
            stubGarments(listOf(testGarment(1, 1)))
            stubNoPlannedEvents()

            val engine = repository()
            engine.suggestOutfits(SuggestionContext(date = TODAY, weather = null, occasionId = null))

            assertEquals(WeatherSource.NONE, engine.lastRunDiagnostics().weatherSource)
        }
}
