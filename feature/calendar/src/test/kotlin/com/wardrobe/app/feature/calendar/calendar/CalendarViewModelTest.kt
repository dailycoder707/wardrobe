package com.wardrobe.app.feature.calendar.calendar

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.feature.calendar.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.calendar.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.calendar.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.calendar.fakes.FakeWardrobeIntelligenceRepository
import com.wardrobe.app.feature.calendar.fakes.FakeWearEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private lateinit var wearEventRepository: FakeWearEventRepository
    private lateinit var viewModel: CalendarViewModel
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        wearEventRepository = FakeWearEventRepository()
        viewModel =
            CalendarViewModel(
                wearEventRepository = wearEventRepository,
                outfitRepository = FakeOutfitRepository(),
                garmentRepository = FakeGarmentRepository(),
                occasionRepository = FakeOccasionRepository(),
                wardrobeIntelligenceRepository = FakeWardrobeIntelligenceRepository(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
                    createdAt = Instant.EPOCH,
                )
            wearEventRepository = FakeWearEventRepository(listOf(existing))
            viewModel =
                CalendarViewModel(
                    wearEventRepository = wearEventRepository,
                    outfitRepository = FakeOutfitRepository(),
                    garmentRepository = FakeGarmentRepository(),
                    occasionRepository = FakeOccasionRepository(),
                    wardrobeIntelligenceRepository = FakeWardrobeIntelligenceRepository(),
                )
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
