package com.wardrobe.app.feature.stats.health

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.feature.stats.fakes.FakeBrandRepository
import com.wardrobe.app.feature.stats.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.stats.fakes.FakeColorRepository
import com.wardrobe.app.feature.stats.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.stats.fakes.FakeStatsRepository
import com.wardrobe.app.feature.stats.fakes.emptyUsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var garmentRepository: FakeGarmentRepository
    private lateinit var categoryRepository: FakeCategoryRepository

    private fun garment(
        id: Long,
        name: String,
        purchaseDate: LocalDate? = null,
        createdAt: Instant = Instant.parse("2020-01-01T00:00:00Z"),
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
        purchaseDate = purchaseDate,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = false,
        images = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun buildViewModel(): HealthViewModel =
        HealthViewModel(
            statsRepository = statsRepository,
            garmentRepository = garmentRepository,
            categoryRepository = categoryRepository,
            brandRepository = FakeBrandRepository(),
            colorRepository = FakeColorRepository(),
            clock = fixedClock,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        statsRepository = FakeStatsRepository()
        garmentRepository = FakeGarmentRepository()
        categoryRepository = FakeCategoryRepository(listOf(Category(CategoryId(1), "Tops", null, CategoryLevel.TOP)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no signal yet reports an empty state`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.isEmpty)
            }
        }

    @Test
    fun `reliable favorites surfaces garments worn well above the median`() =
        runTest {
            garmentRepository =
                FakeGarmentRepository(
                    listOf(garment(1, "Everyday Tee"), garment(2, "Occasion Dress"), garment(3, "Rarely Worn Scarf")),
                )
            statsRepository.costPerWear.value =
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 20, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 2, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(3), totalWearCount = 1, costPerWear = null, lastWornDate = null),
                )
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.advisories.isNotEmpty() }
                val favorites = state.advisories.first { it.title == "Reliable Favorites" }
                assertTrue(favorites.items.any { it.title == "Everyday Tee" })
            }
        }

    @Test
    fun `neglected items surfaces long-dormant garments gently`() =
        runTest {
            garmentRepository = FakeGarmentRepository(listOf(garment(1, "Blazer")))
            statsRepository.dormantItems.value =
                listOf(DormantItem(GarmentId(1), lastWornDate = LocalDate.of(2026, 1, 1)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.advisories.isNotEmpty() }
                assertTrue(state.advisories.any { it.title == "Ready for a Turn" })
            }
        }

    @Test
    fun `unused purchases only flags recently bought, never-worn garments`() =
        runTest {
            garmentRepository =
                FakeGarmentRepository(
                    listOf(
                        garment(1, "New Boots", purchaseDate = LocalDate.of(2026, 6, 1)),
                        garment(2, "Old Never-Worn Coat", purchaseDate = LocalDate.of(2020, 1, 1)),
                    ),
                )
            statsRepository.costPerWear.value =
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 0, costPerWear = null, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 0, costPerWear = null, lastWornDate = null),
                )
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.advisories.isNotEmpty() }
                val advisory = state.advisories.first { it.title == "Recent Purchases to Try" }
                assertTrue(advisory.items.map { it.title } == listOf("New Boots"))
            }
        }

    @Test
    fun `recently refreshed only flags newly added garments already worn`() =
        runTest {
            garmentRepository =
                FakeGarmentRepository(listOf(garment(1, "Fresh Cardigan", createdAt = Instant.now(fixedClock))))
            statsRepository.costPerWear.value =
                listOf(CostPerWearEntry(GarmentId(1), totalWearCount = 2, costPerWear = null, lastWornDate = null))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.advisories.isNotEmpty() }
                assertTrue(state.advisories.any { it.title == "Off to a Great Start" })
            }
        }

    @Test
    fun `unexplored categories flags top-level categories with zero garments`() =
        runTest {
            categoryRepository =
                FakeCategoryRepository(
                    listOf(
                        Category(CategoryId(1), "Tops", null, CategoryLevel.TOP),
                        Category(CategoryId(2), "Accessories", null, CategoryLevel.TOP),
                    ),
                )
            garmentRepository = FakeGarmentRepository(listOf(garment(1, "Shirt")))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.advisories.isNotEmpty() }
                val advisory = state.advisories.first { it.title == "Unexplored Categories" }
                assertTrue(advisory.message.contains("Accessories"))
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<HealthUiState>.awaitMatching(
        predicate: (HealthUiState) -> Boolean,
    ): HealthUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
