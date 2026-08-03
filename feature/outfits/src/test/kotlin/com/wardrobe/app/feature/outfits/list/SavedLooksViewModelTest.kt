package com.wardrobe.app.feature.outfits.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.feature.outfits.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.outfits.fakes.FakeWearEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class SavedLooksViewModelTest {
    private lateinit var outfitRepository: FakeOutfitRepository
    private lateinit var viewModel: SavedLooksViewModel

    private fun outfit(
        id: Long,
        name: String,
        createdAt: Instant,
        isFavorite: Boolean = false,
        isArchived: Boolean = false,
    ) = Outfit(
        id = OutfitId(id),
        name = name,
        garments =
            listOf(
                OutfitGarmentSlot(
                    com.wardrobe.app.core.model.common
                        .GarmentId(1),
                    0,
                ),
            ),
        occasionId = null,
        source = OutfitSource.USER_CREATED,
        isSaved = true,
        isFavorite = isFavorite,
        isArchived = isArchived,
        photoUri = null,
        createdAt = createdAt,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        outfitRepository =
            FakeOutfitRepository(
                listOf(
                    outfit(1, "Weekend Look", Instant.ofEpochSecond(100)),
                    outfit(2, "Date Night", Instant.ofEpochSecond(200), isFavorite = true),
                    outfit(3, "Archived Look", Instant.ofEpochSecond(300), isArchived = true),
                ),
            )
        viewModel =
            SavedLooksViewModel(
                outfitRepository = outfitRepository,
                garmentRepository = FakeGarmentRepository(),
                wearEventRepository = FakeWearEventRepository(),
                occasionRepository = FakeOccasionRepository(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `archived looks are excluded by default`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitLoaded()
                assertEquals(setOf("Weekend Look", "Date Night"), state.outfits.map { it.title }.toSet())
            }
        }

    @Test
    fun `favorite filter shows only favorited looks`() =
        runTest {
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onFiltersChange(SavedLooksFilterState.EMPTY.copy(favoriteOnly = true))
                val state = awaitMatching { it.filters.favoriteOnly && it.outfits.size == 1 }
                assertEquals("Date Night", state.outfits.single().title)
            }
        }

    @Test
    fun `toggling favorite persists through the repository`() =
        runTest {
            viewModel.uiState.test {
                val loaded = awaitLoaded()
                val target = loaded.outfits.first { it.title == "Weekend Look" }
                viewModel.onToggleFavorite(target.id, true)
                val state = awaitMatching { it.outfits.first { o -> o.id == target.id }.isFavorite }
                assertTrue(state.outfits.first { it.id == target.id }.isFavorite)
            }
        }

    @Test
    fun `duplicating a look creates an independent copy`() =
        runTest {
            viewModel.uiState.test {
                val loaded = awaitLoaded()
                viewModel.onDuplicate(loaded.outfits.first { it.title == "Weekend Look" }.id)
                awaitMatching { it.outfits.size == 3 }
            }
            val outfits = outfitRepository.observeOutfits(OutfitFilter(isSaved = null, isArchived = null)).first()
            assertEquals(4, outfits.size)
            assertTrue(outfits.any { it.name == "Weekend Look copy" })
        }

    @Test
    fun `archiving a look removes it from the default view`() =
        runTest {
            viewModel.uiState.test {
                val loaded = awaitLoaded()
                val target = loaded.outfits.first { it.title == "Date Night" }
                viewModel.onArchive(target.id, true)
                val state = awaitMatching { it.outfits.size == 1 }
                assertEquals("Weekend Look", state.outfits.single().title)
            }
        }

    private suspend fun ReceiveTurbine<SavedLooksUiState>.awaitLoaded(): SavedLooksUiState =
        awaitMatching { !it.isLoading }

    private suspend fun ReceiveTurbine<SavedLooksUiState>.awaitMatching(
        predicate: (SavedLooksUiState) -> Boolean,
    ): SavedLooksUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
