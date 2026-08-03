package com.wardrobe.app.feature.outfits.builder

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.feature.outfits.fakes.FakeBrandRepository
import com.wardrobe.app.feature.outfits.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.outfits.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.outfits.fakes.FakeTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** `SavedStateHandle.toRoute<T>()` round-trips arguments through a real
 * `android.os.Bundle` even when only reading them back — a genuine
 * `androidx.navigation` implementation detail, not something specific to
 * this test — so this needs Robolectric like every other Android-framework-
 * touching test in this codebase, not plain JUnit. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OutfitBuilderViewModelTest {
    private lateinit var garmentRepository: FakeGarmentRepository
    private lateinit var outfitRepository: FakeOutfitRepository
    private val categoryId = CategoryId(1)

    private fun garment(
        id: Long,
        name: String,
    ) = Garment(
        id = GarmentId(id),
        name = name,
        categoryId = categoryId,
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
    )

    private fun createViewModel(outfitId: Long = 0L): OutfitBuilderViewModel =
        OutfitBuilderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("outfitId" to outfitId)),
            garmentRepository = garmentRepository,
            outfitRepository = outfitRepository,
            categoryRepository = FakeCategoryRepository(),
            brandRepository = FakeBrandRepository(),
            occasionRepository = FakeOccasionRepository(),
            tagRepository = FakeTagRepository(),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        garmentRepository =
            FakeGarmentRepository(
                listOf(garment(1, "Blue Shirt"), garment(2, "Black Jeans"), garment(3, "Red Dress")),
            )
        outfitRepository = FakeOutfitRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `placing a garment in the dress slot clears top and bottom`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onPlaceGarment(OutfitSlot.TOP, GarmentId(1))
                awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }
                viewModel.onPlaceGarment(OutfitSlot.BOTTOM, GarmentId(2))
                awaitMatching { it.slots.containsKey(OutfitSlot.BOTTOM) }

                viewModel.onPlaceGarment(OutfitSlot.DRESS, GarmentId(3))
                val state = awaitMatching { it.slots.containsKey(OutfitSlot.DRESS) }

                assertFalse(state.slots.containsKey(OutfitSlot.TOP))
                assertFalse(state.slots.containsKey(OutfitSlot.BOTTOM))
            }
        }

    @Test
    fun `undo restores the previous slot arrangement and redo reapplies it`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onPlaceGarment(OutfitSlot.TOP, GarmentId(1))
                awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }

                viewModel.onUndo()
                var state = awaitMatching { !it.slots.containsKey(OutfitSlot.TOP) }
                assertTrue(state.isEmpty)

                viewModel.onRedo()
                state = awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }
                assertTrue(state.canUndo)
            }
        }

    @Test
    fun `quick add places into the first empty slot in dressing order`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onQuickAddGarment(GarmentId(1))
                val state = awaitMatching { it.slots.isNotEmpty() }
                assertTrue(state.slots.containsKey(OutfitSlot.TOP))
            }
        }

    @Test
    fun `clear outfit empties all slots and can be undone`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onPlaceGarment(OutfitSlot.TOP, GarmentId(1))
                awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }

                viewModel.onClearOutfit()
                val cleared = awaitMatching { it.isEmpty }
                assertTrue(cleared.canUndo)
            }
        }

    @Test
    fun `saving a new outfit persists the filled slots`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onPlaceGarment(OutfitSlot.TOP, GarmentId(1))
                awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }

                viewModel.onSave()
                awaitMatching { it.didSave }
            }
            val saved = outfitRepository.observeOutfits(OutfitFilter()).first()
            assertEquals(1, saved.size)
            assertEquals(
                GarmentId(1),
                saved
                    .single()
                    .garments
                    .single()
                    .garmentId,
            )
        }

    @Test
    fun `saving an empty outfit shows a toast instead of persisting`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.uiState.test {
                awaitLoaded()
                viewModel.onSave()
                val state = awaitMatching { it.toastMessage != null }
                assertFalse(state.didSave)
            }
        }

    @Test
    fun `builder state survives process recreation via SavedStateHandle`() =
        runTest {
            val firstHandle = SavedStateHandle(mapOf("outfitId" to 0L))
            val first =
                OutfitBuilderViewModel(
                    savedStateHandle = firstHandle,
                    garmentRepository = garmentRepository,
                    outfitRepository = outfitRepository,
                    categoryRepository = FakeCategoryRepository(),
                    brandRepository = FakeBrandRepository(),
                    occasionRepository = FakeOccasionRepository(),
                    tagRepository = FakeTagRepository(),
                )
            first.uiState.test {
                awaitLoaded()
                first.onPlaceGarment(OutfitSlot.TOP, GarmentId(1))
                awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }
            }

            // Simulate process recreation: a brand new ViewModel instance backed by
            // the same (now-populated) SavedStateHandle — exactly what Android
            // re-delivers after process death, per this test's own name.
            val recreated =
                OutfitBuilderViewModel(
                    savedStateHandle = firstHandle,
                    garmentRepository = garmentRepository,
                    outfitRepository = outfitRepository,
                    categoryRepository = FakeCategoryRepository(),
                    brandRepository = FakeBrandRepository(),
                    occasionRepository = FakeOccasionRepository(),
                    tagRepository = FakeTagRepository(),
                )
            recreated.uiState.test {
                val state = awaitMatching { it.slots.containsKey(OutfitSlot.TOP) }
                assertEquals(GarmentId(1).value, state.slots.getValue(OutfitSlot.TOP).id)
            }
        }

    private suspend fun ReceiveTurbine<OutfitBuilderUiState>.awaitLoaded(): OutfitBuilderUiState =
        awaitMatching {
            !it.isLoading
        }

    private suspend fun ReceiveTurbine<OutfitBuilderUiState>.awaitMatching(
        predicate: (OutfitBuilderUiState) -> Boolean,
    ): OutfitBuilderUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
