package com.wardrobe.app.feature.stats.story

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.Money
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.feature.stats.fakes.FakeColorRepository
import com.wardrobe.app.feature.stats.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.stats.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.stats.fakes.FakeStatsRepository
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
class StoryViewModelTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var garmentRepository: FakeGarmentRepository

    private fun garment(
        id: Long,
        name: String,
        price: Money? = null,
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
        price = price,
        purchaseDate = null,
        condition = null,
        careNotes = null,
        status = GarmentStatus.ACTIVE,
        isReviewed = true,
        isFavorite = false,
        images = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun buildViewModel(): StoryViewModel =
        StoryViewModel(
            statsRepository = statsRepository,
            garmentRepository = garmentRepository,
            outfitRepository = FakeOutfitRepository(),
            colorRepository = FakeColorRepository(listOf(Color(ColorId(1), "Navy", "#1B2A4A"))),
            clock = fixedClock,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        statsRepository = FakeStatsRepository()
        garmentRepository = FakeGarmentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no cards yet reports an empty state rather than a broken screen`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.isEmpty)
            }
        }

    @Test
    fun `outfits worn this year becomes a real headline`() =
        runTest {
            statsRepository.outfitWearEventCount.value = 12
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.cards.isNotEmpty() }
                assertTrue(state.cards.any { it.headline.contains("12 outfits this year") })
            }
        }

    @Test
    fun `favourite color card resolves the color name, not the raw id`() =
        runTest {
            statsRepository.usageStats.value =
                emptyUsageStats(StatsWindow.ONE_YEAR).copy(signatureColorIds = listOf(ColorId(1)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.cards.isNotEmpty() }
                assertTrue(state.cards.any { it.headline.contains("Navy") })
            }
        }

    @Test
    fun `longest unworn card is skipped for a garment added too recently to flag`() =
        runTest {
            garmentRepository =
                FakeGarmentRepository(listOf(garment(1, "New Coat", createdAt = Instant.now(fixedClock))))
            statsRepository.dormantItems.value = listOf(DormantItem(GarmentId(1), lastWornDate = null))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.cards.none { it.headline.contains("New Coat") })
            }
        }

    @Test
    fun `cost saved is never shown when rewearing spans more than one currency`() =
        runTest {
            garmentRepository =
                FakeGarmentRepository(
                    listOf(
                        garment(1, "Dress", price = Money(100.0, "GBP")),
                        garment(2, "Jacket", price = Money(80.0, "USD")),
                    ),
                )
            statsRepository.costPerWear.value =
                listOf(
                    CostPerWearEntry(GarmentId(1), totalWearCount = 4, costPerWear = 25.0, lastWornDate = null),
                    CostPerWearEntry(GarmentId(2), totalWearCount = 4, costPerWear = 20.0, lastWornDate = null),
                )
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.cards.none { it.headline.contains("saved") })
            }
        }

    @Test
    fun `weekend style card only appears when weekday and weekend dress codes actually differ`() =
        runTest {
            statsRepository.topWeekendDressCode.value = DressCode.CASUAL
            statsRepository.topWeekdayDressCode.value = DressCode.BUSINESS
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitMatching { it.cards.isNotEmpty() }
                assertTrue(state.cards.any { it.headline.contains("casual") && it.headline.contains("business") })
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<StoryUiState>.awaitMatching(
        predicate: (StoryUiState) -> Boolean,
    ): StoryUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
