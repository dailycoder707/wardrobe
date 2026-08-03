package com.wardrobe.app.feature.trips.list

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.model.trip.Trip
import com.wardrobe.app.feature.trips.fakes.FakeTripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun trip(
        id: Long,
        name: String?,
        destination: String,
        start: LocalDate,
    ) = Trip(
        id = TripId(id),
        name = name,
        destination = destination,
        dateRange = DateRange(start, start.plusDays(3)),
        activities = emptyList(),
        luggageSize = LuggageSize.CARRY_ON,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `lists trips sorted by start date, falling back to destination when unnamed`() =
        runTest {
            val repository =
                FakeTripRepository(
                    initialTrips =
                        listOf(
                            trip(1, "City Break", "Paris", LocalDate.of(2026, 8, 1)),
                            trip(2, null, "Rome", LocalDate.of(2026, 7, 1)),
                        ),
                )
            val viewModel = TripsViewModel(repository)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.trips.isEmpty()) state = awaitItem()
                assertEquals(listOf("Rome", "City Break"), state.trips.map { it.name })
            }
        }

    @Test
    fun `createTrip saves a new trip via the repository`() =
        runTest {
            val repository = FakeTripRepository()
            val viewModel = TripsViewModel(repository)

            viewModel.createTrip(
                name = "Weekend Away",
                destination = "Bath",
                startDate = LocalDate.of(2026, 9, 1),
                endDate = LocalDate.of(2026, 9, 3),
                luggageSize = LuggageSize.CARRY_ON,
            )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.trips.isEmpty()) state = awaitItem()
                assertEquals("Weekend Away", state.trips.single().name)
            }
        }

    @Test
    fun `deleteTrip removes the trip via the repository`() =
        runTest {
            val repository =
                FakeTripRepository(initialTrips = listOf(trip(1, "City Break", "Paris", LocalDate.of(2026, 8, 1))))
            val viewModel = TripsViewModel(repository)

            viewModel.deleteTrip(1L)

            viewModel.uiState.test {
                var state = awaitItem()
                while (repository.deletedTripId == null) state = awaitItem()
                assertNull(state.trips.firstOrNull { it.id == 1L })
            }
        }
}
