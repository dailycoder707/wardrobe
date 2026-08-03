package com.wardrobe.app.feature.closet.home

import app.cash.turbine.test
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.feature.closet.fakes.FakeBrandRepository
import com.wardrobe.app.feature.closet.fakes.FakeCategoryRepository
import com.wardrobe.app.feature.closet.fakes.FakeColorRepository
import com.wardrobe.app.feature.closet.fakes.FakeGarmentRepository
import com.wardrobe.app.feature.closet.fakes.FakeImportQueueRepository
import com.wardrobe.app.feature.closet.fakes.FakeOutfitRepository
import com.wardrobe.app.feature.closet.fakes.FakePersonalizationRepository
import com.wardrobe.app.feature.closet.fakes.FakeStatsRepository
import com.wardrobe.app.feature.closet.fakes.FakeStylingEngineRepository
import com.wardrobe.app.feature.closet.fakes.FakeSyncRepository
import com.wardrobe.app.feature.closet.fakes.FakeTripRepository
import com.wardrobe.app.feature.closet.fakes.FakeWardrobeIntelligenceRepository
import com.wardrobe.app.feature.closet.fakes.FakeWearEventRepository
import com.wardrobe.app.feature.closet.fakes.FakeWeatherRepository
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
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC)
    private val categoryId = CategoryId(1)

    private fun garment(
        id: Long,
        name: String,
        createdAt: Instant,
        isReviewed: Boolean = true,
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
        isReviewed = isReviewed,
        isFavorite = false,
        images = emptyList(),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `greeting uses the editable display name`() =
        runTest {
            val viewModel =
                buildViewModel(
                    personalization =
                        FakePersonalizationRepository(
                            PersonalizationSettings.DEFAULT.copy(
                                displayName = "Palak",
                                greetingStyle = GreetingStyle.WELCOME_BACK,
                            ),
                        ),
                )
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("Welcome back, Palak", state.greeting)
            }
        }

    @Test
    fun `recently added lists garments newest first`() =
        runTest {
            val viewModel =
                buildViewModel(
                    garments =
                        FakeGarmentRepository(
                            listOf(
                                garment(1, "Old Item", Instant.parse("2026-01-01T00:00:00Z")),
                                garment(2, "New Item", Instant.parse("2026-06-01T00:00:00Z")),
                            ),
                        ),
                )
            viewModel.uiState.test {
                val state = awaitMatching { it.recentlyAdded.isNotEmpty() }
                assertEquals(listOf("New Item", "Old Item"), state.recentlyAdded.map { it.title })
            }
        }

    @Test
    fun `continue editing surfaces only unreviewed garments`() =
        runTest {
            val viewModel =
                buildViewModel(
                    garments =
                        FakeGarmentRepository(
                            listOf(
                                garment(1, "Reviewed", Instant.parse("2026-01-01T00:00:00Z"), isReviewed = true),
                                garment(2, "Needs Review", Instant.parse("2026-01-02T00:00:00Z"), isReviewed = false),
                            ),
                        ),
                )
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(listOf("Needs Review"), state.continueEditing.map { it.title })
            }
        }

    @Test
    fun `recently worn resolves garments from wear events within the window`() =
        runTest {
            val garmentRepository =
                FakeGarmentRepository(
                    listOf(
                        garment(1, "Worn Item", Instant.parse("2026-01-01T00:00:00Z")),
                        garment(2, "Unworn Item", Instant.parse("2026-01-01T00:00:00Z")),
                    ),
                )
            val wearEventRepository =
                FakeWearEventRepository(
                    listOf(
                        WearEvent(
                            id = WearEventId(1),
                            date = LocalDate.of(2026, 6, 10),
                            garmentId = GarmentId(1),
                            outfitId = null,
                            weatherCacheId = null,
                            occasionId = null,
                            note = null,
                            createdAt = Instant.now(fixedClock),
                        ),
                    ),
                )
            val viewModel = buildViewModel(garments = garmentRepository, wearEvents = wearEventRepository)
            viewModel.uiState.test {
                val state = awaitMatching { it.recentlyWorn.isNotEmpty() }
                assertEquals(listOf("Worn Item"), state.recentlyWorn.map { it.title })
            }
        }

    private fun buildViewModel(
        personalization: FakePersonalizationRepository = FakePersonalizationRepository(),
        garments: FakeGarmentRepository = FakeGarmentRepository(),
        wearEvents: FakeWearEventRepository = FakeWearEventRepository(),
    ) = HomeViewModel(
        personalizationRepository = personalization,
        garmentRepository = garments,
        categoryRepository = FakeCategoryRepository(listOf(Category(categoryId, "Tops", null, CategoryLevel.TOP))),
        brandRepository = FakeBrandRepository(),
        insightsRepositories =
            HomeInsightsRepositories(
                colorRepository = FakeColorRepository(),
                outfitRepository = FakeOutfitRepository(),
                wearEventRepository = wearEvents,
                statsRepository = FakeStatsRepository(),
                importQueueRepository = FakeImportQueueRepository(),
            ),
        assistantRepositories =
            HomeAssistantRepositories(
                FakeWeatherRepository(),
                FakeStylingEngineRepository(),
                FakeSyncRepository(),
                FakeWardrobeIntelligenceRepository(),
                FakeTripRepository(),
            ),
        clock = fixedClock,
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<HomeUiState>.awaitMatching(
        predicate: (HomeUiState) -> Boolean,
    ): HomeUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }
}
