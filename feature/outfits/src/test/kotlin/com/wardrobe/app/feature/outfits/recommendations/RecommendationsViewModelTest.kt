package com.wardrobe.app.feature.outfits.recommendations

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.feature.outfits.fakes.FakeBrandRepository
import com.wardrobe.app.feature.outfits.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.outfits.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.outfits.fakes.FakeStyleRuleRepository
import com.wardrobe.app.feature.outfits.fakes.FakeStylingEngineRepository
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var garmentRepository: FakeGarmentRepository
    private lateinit var outfitRepository: FakeOutfitRepository
    private lateinit var wearEventRepository: FakeWearEventRepository
    private lateinit var stylingEngineRepository: FakeStylingEngineRepository

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

    private fun scoredOutfit(): ScoredOutfit =
        ScoredOutfit(
            outfit =
                Outfit(
                    id = OutfitId(0),
                    name = null,
                    garments = listOf(OutfitGarmentSlot(GarmentId(1), OutfitSlot.TOP.slotIndex)),
                    occasionId = null,
                    source = OutfitSource.AI_SUGGESTED,
                    isSaved = false,
                    photoUri = null,
                    createdAt = Instant.now(fixedClock),
                ),
            score = 5.0,
            explanation = "It's one of your favorites.",
            passedWeatherFilter = true,
        )

    private fun buildViewModel(): RecommendationsViewModel =
        RecommendationsViewModel(
            stylingEngineRepository = stylingEngineRepository,
            garmentRepository = garmentRepository,
            categoryRepository =
                FakeCategoryRepository(
                    listOf(Category(CategoryId(1), "Tops", null, CategoryLevel.TOP)),
                ),
            brandRepository = FakeBrandRepository(),
            outfitRepository = outfitRepository,
            wearEventRepository = wearEventRepository,
            styleRuleRepository = FakeStyleRuleRepository(),
            clock = fixedClock,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        garmentRepository = FakeGarmentRepository(listOf(garment(1, "Blazer")))
        outfitRepository = FakeOutfitRepository()
        wearEventRepository = FakeWearEventRepository()
        stylingEngineRepository = FakeStylingEngineRepository(suggestions = listOf(scoredOutfit()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load reflects the engine's suggestions`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(1, state.suggestions.size)
                assertEquals(
                    "Blazer",
                    state.selected
                        ?.items
                        ?.single()
                        ?.tile
                        ?.title,
                )
            }
        }

    @Test
    fun `empty engine result reports an empty state, not a loading spinner`() =
        runTest {
            stylingEngineRepository = FakeStylingEngineRepository(suggestions = emptyList())
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.isEmpty)
            }
        }

    @Test
    fun `wear today saves the suggested outfit and logs a WORN event for today`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading }
                viewModel.wearToday()
                awaitMatching { it.actionMessage != null }
            }
            val today = LocalDate.now(fixedClock)
            assertEquals(1, outfitRepository.observeOutfits().first().size)
            val loggedEvent = wearEventRepository.observeEvents(DateRange(today, today)).first().single()
            assertEquals(WearEventStatus.WORN, loggedEvent.status)
        }

    @Test
    fun `replace slot swaps in the engine's suggested replacement`() =
        runTest {
            garmentRepository = FakeGarmentRepository(listOf(garment(1, "Blazer"), garment(2, "Cardigan")))
            stylingEngineRepository =
                FakeStylingEngineRepository(suggestions = listOf(scoredOutfit()), replacementId = GarmentId(2))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading }
                viewModel.replaceSlot(OutfitSlot.TOP)
                val state =
                    awaitMatching {
                        it.selected
                            ?.items
                            ?.singleOrNull()
                            ?.tile
                            ?.title == "Cardigan"
                    }
                assertEquals(
                    "Cardigan",
                    state.selected
                        ?.items
                        ?.single()
                        ?.tile
                        ?.title,
                )
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<RecommendationsUiState>.awaitMatching(
        predicate: (RecommendationsUiState) -> Boolean,
    ): RecommendationsUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
