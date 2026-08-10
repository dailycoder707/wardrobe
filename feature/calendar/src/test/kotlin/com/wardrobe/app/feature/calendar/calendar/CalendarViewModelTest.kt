package com.wardrobe.app.feature.calendar.calendar

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.common.WeatherCacheId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.feature.calendar.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.calendar.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.calendar.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.calendar.fakes.FakeStatsRepository
import com.wardrobe.app.feature.calendar.fakes.FakeStylingEngineRepository
import com.wardrobe.app.feature.calendar.fakes.FakeWardrobeIntelligenceRepository
import com.wardrobe.app.feature.calendar.fakes.FakeWearEventRepository
import com.wardrobe.app.feature.calendar.fakes.FakeWeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private lateinit var wearEventRepository: FakeWearEventRepository
    private lateinit var outfitRepository: FakeOutfitRepository
    private lateinit var occasionRepository: FakeOccasionRepository
    private lateinit var stylingEngineRepository: FakeStylingEngineRepository
    private lateinit var weatherRepository: FakeWeatherRepository
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var viewModel: CalendarViewModel

    /** M20 — a fixed [Clock] (previously this test relied on the real wall
     * clock via `LocalDate.now()`, matching a pre-M20 bug in `CalendarViewModel`
     * itself that computed "today" the same ungoverned way) makes every date
     * boundary in these tests deterministic instead of flaky around midnight. */
    private val today: LocalDate = LocalDate.of(2026, 8, 10)
    private val fixedClock: Clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        wearEventRepository = FakeWearEventRepository()
        outfitRepository = FakeOutfitRepository()
        occasionRepository = FakeOccasionRepository()
        stylingEngineRepository = FakeStylingEngineRepository()
        weatherRepository = FakeWeatherRepository()
        statsRepository = FakeStatsRepository()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): CalendarViewModel =
        CalendarViewModel(
            wearEventRepository = wearEventRepository,
            outfitRepository = outfitRepository,
            garmentRepository = FakeGarmentRepository(),
            occasionRepository = occasionRepository,
            wardrobeIntelligenceRepository = FakeWardrobeIntelligenceRepository(),
            stylingEngineRepository = stylingEngineRepository,
            weatherRepository = weatherRepository,
            statsRepository = statsRepository,
            clock = fixedClock,
        )

    @Test
    fun `a repository failure surfaces an honest error state instead of loading forever`() =
        runTest {
            wearEventRepository =
                FakeWearEventRepository().apply { observeEventsError = RuntimeException("db unavailable") }
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("Couldn't load your calendar. Try again.", state.error)
            }
        }

    private fun scoredOutfit(
        outfitId: Long = 0,
        garmentId: Long = 1,
        explanation: String = "It fits your usual style.",
    ) = ScoredOutfit(
        outfit =
            Outfit(
                id = OutfitId(outfitId),
                name = "Suggested Look",
                garments = listOf(OutfitGarmentSlot(GarmentId(garmentId), OutfitSlot.TOP.slotIndex)),
                occasionId = null,
                source = OutfitSource.AI_SUGGESTED,
                isSaved = false,
                photoUri = null,
                createdAt = Instant.now(fixedClock),
            ),
        score = 8.0,
        explanation = explanation,
        passedWeatherFilter = true,
    )

    private fun weatherSnapshot(
        date: LocalDate,
        tempHighC: Double = 24.0,
        tempLowC: Double = 14.0,
        currentTempC: Double? = null,
        condition: WeatherCondition = WeatherCondition.SUNNY,
    ) = WeatherSnapshot(
        id = WeatherCacheId(1),
        latitude = 0.0,
        longitude = 0.0,
        date = date,
        fetchedAt = Instant.now(fixedClock),
        tempHighC = tempHighC,
        tempLowC = tempLowC,
        apparentTempHighC = null,
        apparentTempLowC = null,
        precipitationProbabilityPercent = null,
        windSpeedKph = null,
        conditionCode = null,
        isStale = false,
        currentTempC = currentTempC,
        feelsLikeC = null,
        humidityPercent = null,
        uvIndex = null,
        condition = condition,
    )

    @Test
    fun `month grid covers every day of the visible month exactly once`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitLoaded()
                val daysInCurrentMonth = state.monthDays.count { it.isCurrentMonth }
                assertEquals(state.visibleMonth.lengthOfMonth(), daysInCurrentMonth)
                assertEquals(1, state.monthDays.count { it.isToday })
            }
        }

    @Test
    fun `logging wear for today is recorded as worn`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                val state = awaitMatching { it.selectedDayEvents.isNotEmpty() }
                assertEquals(WearEventStatus.WORN, state.selectedDayEvents.single().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `logging wear for a future date is recorded as planned`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                val future = today.plusDays(7)
                viewModel.onSelectDate(future)
                awaitMatching { it.selectedDate == future }
                viewModel.actions.onLogGarmentWear(GarmentId(1), future)
                val state = awaitMatching { it.selectedDayEvents.isNotEmpty() }
                assertEquals(WearEventStatus.PLANNED, state.selectedDayEvents.single().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clear day removes every event logged that day`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                awaitMatching { it.selectedDayEvents.isNotEmpty() }
                viewModel.actions.onClearDay(today)
                val state = awaitMatching { it.selectedDayEvents.isEmpty() }
                assertTrue(state.selectedDayEvents.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `duplicate day copies events onto the target date as planned`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                awaitMatching { it.selectedDayEvents.isNotEmpty() }

                viewModel.actions.onDuplicateDay(today, today.plusDays(7))
                viewModel.onSelectDate(today.plusDays(7))
                val state = awaitMatching { it.selectedDate == today.plusDays(7) && it.selectedDayEvents.isNotEmpty() }
                assertEquals(WearEventStatus.PLANNED, state.selectedDayEvents.single().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirming a planned event marks it worn`() =
        runTest {
            val existing =
                WearEvent(
                    id = WearEventId(1),
                    date = today,
                    garmentId = GarmentId(1),
                    outfitId = null,
                    weatherCacheId = null,
                    occasionId = null,
                    note = null,
                    status = WearEventStatus.PLANNED,
                    createdAt = Instant.now(fixedClock),
                )
            wearEventRepository = FakeWearEventRepository(listOf(existing))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onConfirmWorn(1L)
                val state = awaitMatching { it.selectedDayEvents.any { e -> e.status == WearEventStatus.WORN } }
                assertEquals(WearEventStatus.WORN, state.selectedDayEvents.single().status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `list view groups only worn events by month`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                awaitMatching { it.selectedDayEvents.isNotEmpty() }

                viewModel.onToggleViewMode()
                val state = awaitMatching { it.viewMode == CalendarViewMode.LIST }
                assertTrue(state.historyByMonth.any { it.events.isNotEmpty() })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting an occasion persists it onto every event already logged that day`() =
        runTest {
            val workOccasion = Occasion(OccasionId(1), "Work")
            occasionRepository = FakeOccasionRepository(listOf(workOccasion))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                awaitMatching { it.selectedDayEvents.isNotEmpty() }

                viewModel.onOccasionSelected(workOccasion.id)
                val state = awaitMatching { it.selectedDayOccasionId == workOccasion.id }
                assertEquals("Work", state.selectedDayEvents.single().occasionName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an occasion picked before anything is logged is attached to the new event`() =
        runTest {
            val workOccasion = Occasion(OccasionId(1), "Work")
            occasionRepository = FakeOccasionRepository(listOf(workOccasion))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onOccasionSelected(workOccasion.id)
                awaitMatching { it.selectedDayOccasionId == workOccasion.id }

                viewModel.actions.onLogGarmentWear(GarmentId(1), today)
                val state = awaitMatching { it.selectedDayEvents.isNotEmpty() }
                assertEquals("Work", state.selectedDayEvents.single().occasionName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weather for a future date within the forecast window shows a real daily summary`() =
        runTest {
            val future = today.plusDays(2)
            weatherRepository = FakeWeatherRepository(weatherSnapshot(date = future))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onSelectDate(future)
                val state =
                    awaitMatching {
                        it.selectedDate == future && it.selectedDateWeather is DateWeatherUiState.Available
                    }
                val available = state.selectedDateWeather as DateWeatherUiState.Available
                assertEquals("14°C–24°C Clear skies expected.", available.summary)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weather is honestly unavailable, never fabricated, when nothing resolves`() =
        runTest {
            weatherRepository = FakeWeatherRepository(snapshot = null)
            viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(DateWeatherUiState.Unavailable, state.selectedDateWeather)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a stale snapshot for a different date is never shown as this date's forecast`() =
        runTest {
            val farFuture = today.plusMonths(2)
            // The repository's own cache fallback can return some *other*
            // day's snapshot when nothing genuine is available this far out —
            // its `.date` field reveals that, and Calendar must not present
            // it as real data for `farFuture`.
            weatherRepository = FakeWeatherRepository(weatherSnapshot(date = today))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onSelectDate(farFuture)
                val state = awaitMatching { it.selectedDate == farFuture }
                assertEquals(DateWeatherUiState.ForecastUnavailableForDate, state.selectedDateWeather)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `requesting a recommendation loads a real suggestion from the M19 engine`() =
        runTest {
            stylingEngineRepository.suggestions = listOf(scoredOutfit())
            viewModel.recommendationState.test {
                assertEquals(DayRecommendationUiState.Idle, awaitItem())
                viewModel.recommendationActions.onRequestRecommendation()
                assertEquals(DayRecommendationUiState.Loading, awaitItem())
                val loaded = awaitItem() as DayRecommendationUiState.Loaded
                assertEquals("Suggested Look", loaded.model.title)
                assertNull(loaded.replacingEventId)
                assertEquals(today, stylingEngineRepository.lastContext?.date)
            }
        }

    @Test
    fun `show another surfaces a genuinely different outfit`() =
        runTest {
            stylingEngineRepository.suggestionsForCount = { count ->
                if (count <= 3) {
                    listOf(scoredOutfit(garmentId = 1, explanation = "It's one of your favorites."))
                } else {
                    listOf(scoredOutfit(garmentId = 2, explanation = "It matches your plans for today."))
                }
            }
            viewModel.recommendationState.test {
                awaitItem()
                viewModel.recommendationActions.onRequestRecommendation()
                awaitItem()
                val first = awaitItem() as DayRecommendationUiState.Loaded

                viewModel.recommendationActions.onShowAnother()
                awaitItem()
                val second = awaitItem() as DayRecommendationUiState.Loaded

                assertTrue(first.model != second.model)
                assertEquals(6, stylingEngineRepository.lastRequestedCount)
            }
        }

    @Test
    fun `planning a recommended outfit persists it as a planned wear event`() =
        runTest {
            val future = today.plusDays(3)
            stylingEngineRepository.suggestions = listOf(scoredOutfit())
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onSelectDate(future)
                awaitMatching { it.selectedDate == future }
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.recommendationState.test {
                awaitItem()
                viewModel.recommendationActions.onRequestRecommendation()
                awaitItem()
                awaitItem()
                viewModel.recommendationActions.onPlanRecommendedOutfit()
                assertEquals(DayRecommendationUiState.Idle, awaitItem())
            }

            val savedOutfit = outfitRepository.getOutfit(OutfitId(1))
            assertEquals("Suggested Look", savedOutfit?.name)
            val plannedEvents = wearEventRepository.observeEvents(DateRange(future, future)).first()
            assertEquals(WearEventStatus.PLANNED, plannedEvents.single().status)
            assertEquals(OutfitId(1), plannedEvents.single().outfitId)
        }

    @Test
    fun `replacing a planned event updates its outfit instead of adding a new event`() =
        runTest {
            val existingOutfit =
                Outfit(
                    id = OutfitId(9),
                    name = "Old Look",
                    garments = listOf(OutfitGarmentSlot(GarmentId(9), OutfitSlot.TOP.slotIndex)),
                    occasionId = null,
                    source = OutfitSource.USER_CREATED,
                    isSaved = true,
                    photoUri = null,
                    createdAt = Instant.now(fixedClock),
                )
            outfitRepository = FakeOutfitRepository(listOf(existingOutfit))
            val existingEvent =
                WearEvent(
                    id = WearEventId(1),
                    date = today,
                    garmentId = null,
                    outfitId = existingOutfit.id,
                    weatherCacheId = null,
                    occasionId = null,
                    note = null,
                    status = WearEventStatus.PLANNED,
                    createdAt = Instant.now(fixedClock),
                )
            wearEventRepository = FakeWearEventRepository(listOf(existingEvent))
            stylingEngineRepository.suggestions = listOf(scoredOutfit(outfitId = 0, garmentId = 2))
            viewModel = buildViewModel()

            viewModel.recommendationState.test {
                awaitItem()
                viewModel.recommendationActions.onReplaceEvent(1L)
                awaitItem()
                val loaded = awaitItem() as DayRecommendationUiState.Loaded
                assertEquals(1L, loaded.replacingEventId)

                viewModel.recommendationActions.onPlanRecommendedOutfit()
                assertEquals(DayRecommendationUiState.Idle, awaitItem())
            }

            val events = wearEventRepository.observeEvents(DateRange(today, today)).first()
            assertEquals(1, events.size)
            assertEquals(WearEventId(1), events.single().id)
            assertEquals(OutfitId(10), events.single().outfitId)
            // The replaced event was still PLANNED (not yet confirmed worn) —
            // replacing its outfit must not silently mark it WORN just
            // because its date happens to be today (M20 bug hunt, Part 18).
            assertEquals(WearEventStatus.PLANNED, events.single().status)
        }

    @Test
    fun `a genuine engine failure surfaces an honest error, never a fake recommendation`() =
        runTest {
            stylingEngineRepository.errorToThrow = RuntimeException("boom")
            viewModel.recommendationState.test {
                awaitItem()
                viewModel.recommendationActions.onRequestRecommendation()
                awaitItem()
                val error = awaitItem() as DayRecommendationUiState.Error
                assertEquals("Couldn't generate a recommendation. Try again.", error.message)
            }
        }

    @Test
    fun `a planned garment worn recently is flagged honestly, from real wear-history data`() =
        runTest {
            val recentEntry =
                CostPerWearEntry(
                    garmentId = GarmentId(1),
                    totalWearCount = 3,
                    costPerWear = 1.0,
                    lastWornDate = today.minusDays(2),
                )
            statsRepository.setCostPerWear(listOf(recentEntry))
            viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                val future = today.plusDays(1)
                viewModel.onSelectDate(future)
                awaitMatching { it.selectedDate == future }
                viewModel.actions.onLogGarmentWear(GarmentId(1), future)
                val state = awaitMatching { it.selectedDayEvents.isNotEmpty() }
                assertEquals(true, state.selectedDayEvents.single().wornRecently)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun ReceiveTurbine<CalendarUiState>.awaitLoaded(): CalendarUiState = awaitMatching { !it.isLoading }

    private suspend fun ReceiveTurbine<CalendarUiState>.awaitMatching(
        predicate: (CalendarUiState) -> Boolean,
    ): CalendarUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
