package com.wardrobe.app.feature.stats.insights

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.RepeatedOutfit
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.feature.stats.fakes.FakeBrandRepository
import com.wardrobe.app.feature.stats.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.stats.fakes.FakeColorRepository
import com.wardrobe.app.feature.stats.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.stats.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.stats.fakes.FakeStatsRepository
import com.wardrobe.app.feature.stats.fakes.FakeWearEventRepository
import com.wardrobe.app.feature.stats.fakes.emptyUsageStats
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var viewModel: InsightsViewModel

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
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        statsRepository = FakeStatsRepository()
        viewModel =
            InsightsViewModel(
                statsRepository = statsRepository,
                garmentRepository = FakeGarmentRepository(listOf(garment(1, "Shirt"), garment(2, "Jeans"))),
                outfitRepository = FakeOutfitRepository(),
                categoryRepository =
                    FakeCategoryRepository(listOf(Category(CategoryId(1), "Tops", null, CategoryLevel.TOP))),
                brandRepository = FakeBrandRepository(listOf(Brand(BrandId(1), "Acme", null))),
                colorRepository = FakeColorRepository(),
                wearEventRepository = FakeWearEventRepository(),
                clock = fixedClock,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `usage overview reflects the usage stats fake`() =
        runTest {
            statsRepository.usageStats.value =
                emptyUsageStats(StatsWindow.ONE_YEAR)
                    .copy(totalActiveGarments = 10, wornAtLeastOnce = 4, usagePercent = 40.0)

            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(10, state.totalActiveGarments)
                assertEquals(4, state.wornAtLeastOnce)
                assertEquals(40, state.usagePercent)
            }
        }

    @Test
    fun `favourite brand ids resolve to names via the brand repository`() =
        runTest {
            statsRepository.usageStats.value =
                emptyUsageStats(StatsWindow.ONE_YEAR).copy(favouriteBrandIds = listOf(BrandId(1)))

            viewModel.uiState.test {
                val state = awaitMatching { it.favoriteBrandNames.isNotEmpty() }
                assertEquals(listOf("Acme"), state.favoriteBrandNames)
            }
        }

    @Test
    fun `most worn list resolves garment names sorted by wear count`() =
        runTest {
            statsRepository.costPerWear.value =
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 5, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 2, costPerWear = null, lastWornDate = null),
                )

            viewModel.uiState.test {
                val state = awaitMatching { it.lists.mostWorn.isNotEmpty() }
                assertEquals(listOf("Shirt", "Jeans"), state.lists.mostWorn.map { it.title })
            }
        }

    @Test
    fun `changing the window re-requests repeated outfits for the new window`() =
        runTest {
            statsRepository.repeatedOutfits.value = listOf(RepeatedOutfit(OutfitId(1), 3))

            viewModel.uiState.test {
                awaitMatching { !it.isLoading }
                viewModel.onWindowChange(StatsWindow.ALL_TIME)
                val state = awaitMatching { it.window == StatsWindow.ALL_TIME }
                assertEquals(StatsWindow.ALL_TIME, state.window)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `garments missing outfits resolves to insight items`() =
        runTest {
            statsRepository.garmentsMissingOutfits.value = listOf(GarmentId(2))

            viewModel.uiState.test {
                val state = awaitMatching { it.lists.garmentsMissingOutfits.isNotEmpty() }
                assertEquals(
                    "Jeans",
                    state.lists.garmentsMissingOutfits
                        .single()
                        .title,
                )
                assertTrue(
                    !state.lists.garmentsMissingOutfits
                        .single()
                        .isOutfit,
                )
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<InsightsUiState>.awaitMatching(
        predicate: (InsightsUiState) -> Boolean,
    ): InsightsUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
