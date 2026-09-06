package com.wardrobe.app.feature.outfits.recommendations

import app.cash.turbine.test
import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WeatherCacheId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.feature.outfits.fakes.FakeAiProviderSettingsRepository
import com.wardrobe.app.feature.outfits.fakes.FakeBrandRepository
import com.wardrobe.app.feature.outfits.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.outfits.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOccasionRepository
import com.wardrobe.app.feature.outfits.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.outfits.fakes.FakeStyleRuleRepository
import com.wardrobe.app.feature.outfits.fakes.FakeStylingEngineRepository
import com.wardrobe.app.feature.outfits.fakes.FakeWearEventRepository
import com.wardrobe.app.feature.outfits.fakes.FakeWeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private lateinit var occasionRepository: FakeOccasionRepository
    private lateinit var weatherRepository: FakeWeatherRepository
    private lateinit var aiProviderSettingsRepository: FakeAiProviderSettingsRepository

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

    private fun scoredOutfit(
        garmentId: Long = 1,
        explanation: String = "It's one of your favorites.",
    ): ScoredOutfit =
        ScoredOutfit(
            outfit =
                Outfit(
                    id = OutfitId(0),
                    name = null,
                    garments = listOf(OutfitGarmentSlot(GarmentId(garmentId), OutfitSlot.TOP.slotIndex)),
                    occasionId = null,
                    source = OutfitSource.AI_SUGGESTED,
                    isSaved = false,
                    photoUri = null,
                    createdAt = Instant.now(fixedClock),
                ),
            score = 5.0,
            explanation = explanation,
            passedWeatherFilter = true,
        )

    private fun weatherSnapshot(
        currentTempC: Double = 18.0,
        condition: WeatherCondition = WeatherCondition.RAIN,
    ) = WeatherSnapshot(
        id = WeatherCacheId(1),
        latitude = 0.0,
        longitude = 0.0,
        date = LocalDate.now(fixedClock),
        fetchedAt = Instant.now(fixedClock),
        tempHighC = currentTempC,
        tempLowC = currentTempC,
        apparentTempHighC = currentTempC,
        apparentTempLowC = currentTempC,
        precipitationProbabilityPercent = null,
        windSpeedKph = null,
        conditionCode = null,
        isStale = false,
        currentTempC = currentTempC,
        condition = condition,
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
            occasionRepository = occasionRepository,
            weatherRepository = weatherRepository,
            aiProviderSettingsRepository = aiProviderSettingsRepository,
            clock = fixedClock,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        garmentRepository = FakeGarmentRepository(listOf(garment(1, "Blazer")))
        outfitRepository = FakeOutfitRepository()
        wearEventRepository = FakeWearEventRepository()
        stylingEngineRepository = FakeStylingEngineRepository(suggestions = listOf(scoredOutfit()))
        occasionRepository = FakeOccasionRepository()
        weatherRepository = FakeWeatherRepository()
        aiProviderSettingsRepository = FakeAiProviderSettingsRepository()
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
    fun `an empty result with existing garments reports insufficient wardrobe, not a genuinely empty one`() =
        runTest {
            stylingEngineRepository = FakeStylingEngineRepository(suggestions = emptyList())
            // buildViewModel()'s default garmentRepository already seeds one garment (Blazer).
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.isEmpty)
                assertTrue(!state.hasNoGarments)
            }
        }

    @Test
    fun `an empty result with a genuinely empty wardrobe reports hasNoGarments, never conflated with insufficient`() =
        runTest {
            stylingEngineRepository = FakeStylingEngineRepository(suggestions = emptyList())
            garmentRepository = FakeGarmentRepository()
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(state.isEmpty)
                assertTrue(state.hasNoGarments)
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

    @Test
    fun `selecting an occasion recomputes and threads it into the engine's context`() =
        runTest {
            val workOccasion = Occasion(OccasionId(1), "Work")
            occasionRepository = FakeOccasionRepository(listOf(workOccasion))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading && it.availableOccasions.isNotEmpty() }
                viewModel.onOccasionSelected(workOccasion.id)
                awaitMatching { it.isLoading }
                awaitMatching { !it.isLoading && it.selectedOccasionId == workOccasion.id }
            }
            assertEquals(workOccasion.id, stylingEngineRepository.lastContext?.occasionId)
        }

    @Test
    fun `selecting the same occasion twice does not trigger a redundant recompute`() =
        runTest {
            val workOccasion = Occasion(OccasionId(1), "Work")
            occasionRepository = FakeOccasionRepository(listOf(workOccasion))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading && it.availableOccasions.isNotEmpty() }
                viewModel.onOccasionSelected(workOccasion.id)
                awaitMatching { it.isLoading }
                awaitMatching { !it.isLoading && it.selectedOccasionId == workOccasion.id }
                val countAfterFirstSelection = stylingEngineRepository.lastRequestedCount
                viewModel.onOccasionSelected(workOccasion.id)
                cancelAndIgnoreRemainingEvents()
                assertEquals(countAfterFirstSelection, stylingEngineRepository.lastRequestedCount)
            }
        }

    @Test
    fun `weather summary reflects the real weather this run used`() =
        runTest {
            val snowSnapshot = weatherSnapshot(currentTempC = 5.0, condition = WeatherCondition.SNOW)
            weatherRepository = FakeWeatherRepository(snowSnapshot)
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("5°C today. Snow expected.", state.weatherSummary)
            }
        }

    @Test
    fun `weather summary is null, never fabricated, when weather is genuinely unavailable`() =
        runTest {
            weatherRepository = FakeWeatherRepository(snapshot = null)
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertNull(state.weatherSummary)
            }
        }

    @Test
    fun `show another requests more candidates and surfaces a genuinely new outfit`() =
        runTest {
            garmentRepository = FakeGarmentRepository(listOf(garment(1, "Blazer"), garment(2, "Cardigan")))
            stylingEngineRepository =
                FakeStylingEngineRepository().apply {
                    suggestionsForCount = { count ->
                        if (count <= 3) {
                            listOf(scoredOutfit(garmentId = 1))
                        } else {
                            listOf(scoredOutfit(garmentId = 1), scoredOutfit(garmentId = 2))
                        }
                    }
                }
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading }
                viewModel.showAnother()
                val state = awaitMatching { it.suggestions.size == 2 }
                assertEquals(1, state.selectedIndex)
                val selectedTitle =
                    state.selected
                        ?.items
                        ?.single()
                        ?.tile
                        ?.title
                assertEquals("Cardigan", selectedTitle)
            }
            assertEquals(6, stylingEngineRepository.lastRequestedCount)
        }

    @Test
    fun `show another reports honestly when no other outfit is available, never repeating silently`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val initial = awaitMatching { !it.isLoading }
                viewModel.showAnother()
                val state = awaitMatching { it.actionMessage != null }
                assertEquals(
                    "No other complete outfit matches this context with your current wardrobe.",
                    state.actionMessage,
                )
                assertEquals(initial.suggestions, state.suggestions)
            }
        }

    @Test
    fun `generation failure surfaces an honest error state, never a fake recommendation`() =
        runTest {
            stylingEngineRepository = FakeStylingEngineRepository().apply { errorToThrow = RuntimeException("boom") }
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { it.isError }
                assertTrue(state.suggestions.isEmpty())
                assertEquals(false, state.isLoading)
                assertEquals("Couldn't generate a recommendation. Try again.", state.errorMessage)
            }
        }

    @Test
    fun `cloud styling activity reflects a real in-flight OUTFIT_STYLING job`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitMatching { !it.isLoading }
                aiProviderSettingsRepository.activeOperationsFlow.value =
                    listOf(AiActiveOperation(AiCapability.OUTFIT_STYLING, AiJobStatus.RUNNING, Instant.now(fixedClock)))
                val state = awaitMatching { it.isCloudStylingActive }
                assertTrue(state.isCloudStylingActive)
            }
        }

    @Test
    fun `an active job for a different capability never marks cloud styling as active`() =
        runTest {
            aiProviderSettingsRepository.activeOperationsFlow.value =
                listOf(AiActiveOperation(AiCapability.GARMENT_METADATA, AiJobStatus.RUNNING, Instant.now(fixedClock)))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(false, state.isCloudStylingActive)
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
