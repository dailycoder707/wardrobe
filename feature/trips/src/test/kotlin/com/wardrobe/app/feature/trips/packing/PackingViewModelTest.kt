package com.wardrobe.app.feature.trips.packing

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.PackingListItemId
import com.wardrobe.app.core.model.common.TripId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.trip.PackingListItem
import com.wardrobe.app.feature.trips.fakes.FakeGarmentRepository
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

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PackingViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun garment(
        id: Long,
        name: String,
    ) = Garment(
        id = GarmentId(id),
        name = name,
        categoryId = CategoryId(1),
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
        brandId = null,
        size = null,
        price = null,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = false,
        images = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        isInLaundry = false,
    )

    private fun packingItem(
        id: Long,
        garmentId: GarmentId?,
        freeTextName: String?,
        category: String,
        isPacked: Boolean = false,
    ) = PackingListItem(
        id = PackingListItemId(id),
        tripId = TripId(1),
        garmentId = garmentId,
        freeTextName = freeTextName,
        category = category,
        isPacked = isPacked,
        rationale = null,
    )

    private fun createViewModel(
        tripRepository: FakeTripRepository,
        garmentRepository: FakeGarmentRepository,
    ): PackingViewModel =
        PackingViewModel(
            savedStateHandle = SavedStateHandle(mapOf("tripId" to 1L)),
            tripRepository = tripRepository,
            garmentRepository = garmentRepository,
        )

    @Test
    fun `groups packing items by category, resolving garment names`() =
        runTest {
            val tripRepository = FakeTripRepository()
            tripRepository.savePackingList(
                TripId(1),
                listOf(
                    packingItem(1, GarmentId(1), null, "Day 1 Outfit"),
                    packingItem(2, null, "Toothbrush", "Toiletries"),
                ),
            )
            val garmentRepository = FakeGarmentRepository(initial = listOf(garment(1, "Blue Shirt")))
            val viewModel = createViewModel(tripRepository, garmentRepository)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.groups.isEmpty()) state = awaitItem()
                val outfitGroup = state.groups.first { it.category == "Day 1 Outfit" }
                assertEquals("Blue Shirt", outfitGroup.items.single().name)
                val toiletriesGroup = state.groups.first { it.category == "Toiletries" }
                assertEquals("Toothbrush", toiletriesGroup.items.single().name)
            }
        }

    @Test
    fun `togglePacked updates the item via the repository`() =
        runTest {
            val tripRepository = FakeTripRepository()
            tripRepository.savePackingList(TripId(1), listOf(packingItem(1, GarmentId(1), null, "Day 1 Outfit")))
            val garmentRepository = FakeGarmentRepository(initial = listOf(garment(1, "Blue Shirt")))
            val viewModel = createViewModel(tripRepository, garmentRepository)

            viewModel.togglePacked(1L, isPacked = true)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.groups.isEmpty() ||
                    !state.groups
                        .single()
                        .items
                        .single()
                        .isPacked
                ) {
                    state = awaitItem()
                }
                assertTrue(
                    state.groups
                        .single()
                        .items
                        .single()
                        .isPacked,
                )
            }
        }
}
