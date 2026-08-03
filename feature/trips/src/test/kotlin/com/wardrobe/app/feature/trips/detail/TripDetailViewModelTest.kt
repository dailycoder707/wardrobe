package com.wardrobe.app.feature.trips.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.model.trip.PackingListItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val trip =
        Trip(
            id = TripId(1),
            name = "City Break",
            destination = "Paris",
            dateRange = DateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)),
            activities = emptyList(),
            luggageSize = LuggageSize.CARRY_ON,
            createdAt = Instant.EPOCH,
        )

    private fun createViewModel(repository: FakeTripRepository): TripDetailViewModel =
        TripDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("tripId" to 1L)),
            tripRepository = repository,
        )

    @Test
    fun `loads the trip referenced by the route`() =
        runTest {
            val repository = FakeTripRepository(initialTrips = listOf(trip))
            val viewModel = createViewModel(repository)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                assertEquals("City Break", state.name)
                assertEquals(false, state.notFound)
            }
        }

    @Test
    fun `missing trip surfaces not found`() =
        runTest {
            val repository = FakeTripRepository()
            val viewModel = createViewModel(repository)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                assertTrue(state.notFound)
            }
        }

    @Test
    fun `generatePackingList saves the generated suggestions`() =
        runTest {
            val repository = FakeTripRepository(initialTrips = listOf(trip))
            repository.generatedSuggestions =
                listOf(
                    PackingListItem(
                        id = PackingListItemId(0),
                        tripId = TripId(1),
                        garmentId = GarmentId(1),
                        freeTextName = null,
                        category = "Day 1 Outfit",
                        isPacked = false,
                        rationale = null,
                    ),
                )
            val viewModel = createViewModel(repository)

            viewModel.generatePackingList()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.packingItemCount == 0) state = awaitItem()
                assertEquals(1, state.packingItemCount)
            }
        }
}
