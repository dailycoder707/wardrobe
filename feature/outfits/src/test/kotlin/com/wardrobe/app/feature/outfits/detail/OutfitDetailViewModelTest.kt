package com.wardrobe.app.feature.outfits.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.feature.outfits.fakes.FakeBrandRepository
import com.wardrobe.app.feature.outfits.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.outfits.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.outfits.fakes.FakeTagRepository
import com.wardrobe.app.feature.outfits.fakes.FakeWardrobeIntelligenceRepository
import com.wardrobe.app.feature.outfits.fakes.FakeWearEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OutfitDetailViewModelTest {
    private lateinit var outfitRepository: FakeOutfitRepository
    private lateinit var wearEventRepository: FakeWearEventRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        routeOutfitId: Long,
        existingOutfitId: Long? = routeOutfitId,
        outfitsWithWearHistory: Set<Long> = emptySet(),
    ): OutfitDetailViewModel {
        outfitRepository =
            FakeOutfitRepository(
                initial =
                    existingOutfitId?.let { id ->
                        listOf(
                            Outfit(
                                id = OutfitId(id),
                                name = "Weekend Look",
                                garments = listOf(OutfitGarmentSlot(GarmentId(1), 0)),
                                occasionId = null,
                                source = OutfitSource.USER_CREATED,
                                isSaved = true,
                                photoUri = null,
                                createdAt = Instant.EPOCH,
                            ),
                        )
                    } ?: emptyList(),
                outfitsWithWearHistory = outfitsWithWearHistory,
            )
        wearEventRepository = FakeWearEventRepository()
        return OutfitDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("outfitId" to routeOutfitId)),
            outfitRepository = outfitRepository,
            garmentRepository = FakeGarmentRepository(),
            categoryRepository = FakeCategoryRepository(),
            brandRepository = FakeBrandRepository(),
            occasionRepository = FakeOccasionRepository(),
            tagRepository = FakeTagRepository(),
            wearEventRepository = wearEventRepository,
            wardrobeIntelligenceRepository = FakeWardrobeIntelligenceRepository(),
        )
    }

    @Test
    fun `loads the outfit referenced by the route`() =
        runTest {
            val viewModel = createViewModel(routeOutfitId = 1L)
            viewModel.uiState.test {
                val state = awaitLoaded()
                assertEquals("Weekend Look", state.title)
            }
        }

    @Test
    fun `missing outfit id surfaces not found`() =
        runTest {
            val viewModel = createViewModel(routeOutfitId = 999L, existingOutfitId = null)
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.notFound)
            }
        }

    @Test
    fun `toggling favorite updates the repository`() =
        runTest {
            val viewModel = createViewModel(routeOutfitId = 1L)
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onToggleFavorite()
                val state = awaitMatching { it.isFavorite }
                assertTrue(state.isFavorite)
            }
        }

    @Test
    fun `deleting an outfit with wear history surfaces a blocked message instead of throwing`() =
        runTest {
            val viewModel = createViewModel(routeOutfitId = 1L, outfitsWithWearHistory = setOf(1L))
            viewModel.uiState.test {
                awaitLoaded()
                var deleted = false
                viewModel.onDelete(onDeleted = { deleted = true })
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.deleteBlockedMessage.test {
                val message = awaitItem() ?: awaitItem()
                assertNotNull(message)
            }
        }

    private suspend fun ReceiveTurbine<OutfitDetailUiState>.awaitLoaded(): OutfitDetailUiState =
        awaitMatching {
            !it.isLoading
        }

    private suspend fun ReceiveTurbine<OutfitDetailUiState>.awaitMatching(
        predicate: (OutfitDetailUiState) -> Boolean,
    ): OutfitDetailUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
