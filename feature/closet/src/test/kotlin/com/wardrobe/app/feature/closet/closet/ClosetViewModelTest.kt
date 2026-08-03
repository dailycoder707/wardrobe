package com.wardrobe.app.feature.closet.closet

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.ImageMetadataId
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.GarmentSortField
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.SortDirection
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.feature.closet.debug.ClosetDiagnostics
import com.wardrobe.app.feature.closet.fakes.FakeBrandRepository
import com.wardrobe.app.feature.closet.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.closet.fakes.FakeClosetPreferencesRepository
import com.wardrobe.app.feature.closet.fakes.FakeColorRepository
import com.wardrobe.app.feature.closet.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.closet.fakes.FakeMaterialRepository
import com.wardrobe.app.feature.closet.fakes.FakeStatsRepository
import com.wardrobe.app.feature.closet.fakes.FakeTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ClosetViewModelTest {
    private lateinit var garmentRepository: FakeGarmentRepository
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var closetPreferencesRepository: FakeClosetPreferencesRepository
    private lateinit var viewModel: ClosetViewModel

    private val categoryId = CategoryId(1)

    private fun garment(
        id: Long,
        name: String,
        createdAt: Instant,
        isFavorite: Boolean = false,
        price: Double? = null,
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
        price =
            price?.let {
                com.wardrobe.app.core.model.common
                    .Money(it, "GBP")
            },
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = isFavorite,
        images = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        garmentRepository =
            FakeGarmentRepository(
                listOf(
                    garment(1, "Blue Shirt", Instant.parse("2026-01-01T00:00:00Z")),
                    garment(2, "Red Dress", Instant.parse("2026-02-01T00:00:00Z"), isFavorite = true),
                    garment(3, "Green Coat", Instant.parse("2026-03-01T00:00:00Z"), price = 100.0),
                ),
            )
        statsRepository = FakeStatsRepository()
        closetPreferencesRepository = FakeClosetPreferencesRepository()
        viewModel =
            ClosetViewModel(
                garmentRepository = garmentRepository,
                statsRepository = statsRepository,
                closetPreferencesRepository = closetPreferencesRepository,
                closetDiagnostics = ClosetDiagnostics(),
                categoryRepository =
                    FakeCategoryRepository(
                        listOf(
                            com.wardrobe.app.core.model.garment
                                .Category(categoryId, "Tops", null, CategoryLevel.TOP),
                        ),
                    ),
                colorRepository = FakeColorRepository(),
                brandRepository = FakeBrandRepository(),
                materialRepository = FakeMaterialRepository(),
                tagRepository = FakeTagRepository(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state lists all garments sorted by recently added descending`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitStateWithGarments()
                assertEquals(listOf("Green Coat", "Red Dress", "Blue Shirt"), state.garments.map { it.title })
            }
        }

    @Test
    fun `search query filters garments by name`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onSearchQueryChange("dress")
                // The reactive pipeline (search -> SQL-level GarmentFilter -> a
                // new garments Flow via flatMapLatest) can emit one transient
                // state where the filter has updated but the garment list
                // hasn't caught up yet — wait for both to settle together.
                val state = awaitMatching { it.searchQuery == "dress" && it.garments.size == 1 }
                assertEquals(listOf("Red Dress"), state.garments.map { it.title })
            }
        }

    @Test
    fun `favorite filter shows only favorited garments`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(favoriteOnly = true))
                val state = awaitMatching { it.filters.favoriteOnly && it.garments.size == 1 }
                assertEquals(listOf("Red Dress"), state.garments.map { it.title })
            }
        }

    @Test
    fun `price range filter excludes garments outside the range`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(priceMin = 50.0))
                val state = awaitMatching { it.filters.priceMin == 50.0 && it.garments.size == 1 }
                assertEquals(listOf("Green Coat"), state.garments.map { it.title })
            }
        }

    @Test
    fun `alphabetical sort ascending orders garments by name`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onSortChange(GarmentSort(GarmentSortField.ALPHABETICAL, SortDirection.ASCENDING))
                val state = awaitMatching { it.sort.field == GarmentSortField.ALPHABETICAL }
                assertEquals(listOf("Blue Shirt", "Green Coat", "Red Dress"), state.garments.map { it.title })
            }
        }

    @Test
    fun `wear count sort uses stats repository data`() =
        runTest {
            statsRepository.setCostPerWear(
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 5, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 1, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(3), totalWearCount = 10, costPerWear = null, lastWornDate = null),
                ),
            )
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onSortChange(GarmentSort(GarmentSortField.WEAR_COUNT, SortDirection.DESCENDING))
                val state = awaitMatching { it.sort.field == GarmentSortField.WEAR_COUNT }
                assertEquals(listOf("Green Coat", "Blue Shirt", "Red Dress"), state.garments.map { it.title })
            }
        }

    @Test
    fun `never worn filter keeps only garments with zero wear count`() =
        runTest {
            statsRepository.setCostPerWear(
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 0, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 3, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(3), totalWearCount = 0, costPerWear = null, lastWornDate = null),
                ),
            )
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(neverWorn = true))
                val state = awaitMatching { it.filters.neverWorn }
                assertEquals(setOf("Blue Shirt", "Green Coat"), state.garments.map { it.title }.toSet())
            }
        }

    @Test
    fun `selection mode toggling tracks selected ids`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.selection.enter(1L)
                var state = awaitMatching { it.isSelectionMode }
                assertEquals(setOf(1L), state.selectedIds)

                viewModel.selection.toggle(2L)
                state = awaitMatching { it.selectedIds.size == 2 }
                assertEquals(setOf(1L, 2L), state.selectedIds)

                viewModel.selection.clear()
                state = awaitMatching { !it.isSelectionMode }
                assertEquals(emptySet<Long>(), state.selectedIds)
            }
        }

    @Test
    fun `beginDelete hides the selected items immediately without deleting them`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.selection.enter(1L)
                awaitMatching { it.isSelectionMode }

                viewModel.selection.beginDelete()
                val state = awaitMatching { it.garments.none { tile -> tile.id == 1L } }

                assertEquals(setOf("Red Dress", "Green Coat"), state.garments.map { it.title }.toSet())
                assertEquals(true, garmentRepository.getGarment(GarmentId(1)) != null)
            }
        }

    @Test
    fun `cancelPendingDeletion restores the hidden items`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.selection.enter(1L)
                awaitMatching { it.isSelectionMode }
                viewModel.selection.beginDelete()
                awaitMatching { it.garments.none { tile -> tile.id == 1L } }

                viewModel.selection.cancelPendingDeletion()
                val state = awaitMatching { it.garments.any { tile -> tile.id == 1L } }

                assertEquals(3, state.garments.size)
                assertEquals(true, garmentRepository.getGarment(GarmentId(1)) != null)
            }
        }

    @Test
    fun `confirmPendingDeletion actually removes the garment from the repository`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.selection.enter(1L)
                awaitMatching { it.isSelectionMode }
                viewModel.selection.beginDelete()
                awaitMatching { it.garments.none { tile -> tile.id == 1L } }

                viewModel.selection.confirmPendingDeletion()
                awaitMatching { it.toastMessage != null }

                assertEquals(null, garmentRepository.getGarment(GarmentId(1)))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clear filters resets to the unfiltered list`() =
        runTest {
            viewModel.uiState.test {
                awaitStateWithGarments()
                viewModel.onFiltersChange(ClosetFilterState.EMPTY.copy(favoriteOnly = true))
                awaitMatching { it.filters.favoriteOnly && it.garments.size == 1 }
                viewModel.onClearFilters()
                val state = awaitMatching { it.filters.activeCount == 0 && it.garments.size == 3 }
                assertEquals(3, state.garments.size)
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<ClosetUiState>.awaitStateWithGarments(): ClosetUiState =
        awaitMatching { !it.isLoading && it.garments.isNotEmpty() }

    private suspend fun app.cash.turbine.ReceiveTurbine<ClosetUiState>.awaitMatching(
        predicate: (ClosetUiState) -> Boolean,
    ): ClosetUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
