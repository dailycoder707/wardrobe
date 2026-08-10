package com.wardrobe.app.feature.closet.home

import app.cash.turbine.test
import com.wardrobe.app.core.model.ai.AiActiveOperation
import com.wardrobe.app.core.model.ai.AiActivityEntry
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiVendor
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
import com.wardrobe.app.feature.closet.fakes.FakeAiProviderSettingsRepository
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
        aiProviderSettings: FakeAiProviderSettingsRepository = FakeAiProviderSettingsRepository(),
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
                aiProviderSettings,
            ),
        clock = fixedClock,
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<HomeAssistantUiState>.awaitAssistantMatching(
        predicate: (HomeAssistantUiState) -> Boolean,
    ): HomeAssistantUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }

    @Test
    fun `recent AI activity is empty, not fabricated, when nothing has ever run`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { !it.isLoading }
                assertEquals(emptyList<AiActivityUiModel>(), state.recentAiActivity)
            }
        }

    @Test
    fun `recent AI activity surfaces real ai_call_log rows, newest first`() =
        runTest {
            val aiProviderSettings =
                FakeAiProviderSettingsRepository().apply {
                    activityFlow.value =
                        listOf(
                            AiActivityEntry(
                                capability = AiCapability.GARMENT_METADATA,
                                provider = null,
                                outcome = AiCallOutcome.SUCCESS,
                                cacheHit = false,
                                timestamp = Instant.now(fixedClock).minusSeconds(30),
                            ),
                        )
                }
            val viewModel = buildViewModel(aiProviderSettings = aiProviderSettings)
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { it.recentAiActivity.isNotEmpty() }
                assertEquals(1, state.recentAiActivity.size)
                assertEquals("Garment analyzed", state.recentAiActivity.first().headline)
            }
        }

    @Test
    fun `cloud AI configured count is zero when every capability is on-device, never fabricated as configured`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { !it.isLoading }
                assertEquals(0, state.cloudAiConfiguredCount)
                assertEquals(AiCapability.entries.size, state.totalAiCapabilities)
            }
        }

    @Test
    fun `cloud AI configured count reflects a real Cloud-ready capability`() =
        runTest {
            val aiProviderSettings =
                FakeAiProviderSettingsRepository().apply {
                    configsFlow.value =
                        configsFlow.value +
                        (
                            AiCapability.OUTFIT_STYLING to
                                AiProviderConfig(
                                    capability = AiCapability.OUTFIT_STYLING,
                                    mode = AiProviderMode.CLOUD,
                                    vendor = AiVendor.OPENAI,
                                    baseUrl = "https://example.test",
                                    model = null,
                                    costRatePerThousandTokens = null,
                                    consentGrantedAt = Instant.EPOCH,
                                    consentHost = "https://example.test",
                                )
                        )
                }
            val viewModel = buildViewModel(aiProviderSettings = aiProviderSettings)
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { it.cloudAiConfiguredCount > 0 }
                assertEquals(1, state.cloudAiConfiguredCount)
            }
        }

    @Test
    fun `active AI operation label is null when nothing is genuinely running`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { !it.isLoading }
                assertEquals(null, state.activeAiOperationLabel)
            }
        }

    @Test
    fun `active AI operation label reflects a real in-flight AiJobManager job`() =
        runTest {
            val aiProviderSettings =
                FakeAiProviderSettingsRepository().apply {
                    activeOperationsFlow.value =
                        listOf(
                            AiActiveOperation(
                                capability = AiCapability.GARMENT_METADATA,
                                status = AiJobStatus.RUNNING,
                                startedAt = Instant.now(fixedClock),
                            ),
                        )
                }
            val viewModel = buildViewModel(aiProviderSettings = aiProviderSettings)
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { it.activeAiOperationLabel != null }
                assertEquals("Analyzing garment…", state.activeAiOperationLabel)
            }
        }

    @Test
    fun `active AI operation label picks the earliest-started job when several are in flight`() =
        runTest {
            val aiProviderSettings =
                FakeAiProviderSettingsRepository().apply {
                    activeOperationsFlow.value =
                        listOf(
                            AiActiveOperation(
                                capability = AiCapability.VIRTUAL_TRY_ON,
                                status = AiJobStatus.PENDING,
                                startedAt = Instant.now(fixedClock),
                            ),
                            AiActiveOperation(
                                capability = AiCapability.GARMENT_EXTRACTION,
                                status = AiJobStatus.RUNNING,
                                startedAt = Instant.now(fixedClock).minusSeconds(10),
                            ),
                        )
                }
            val viewModel = buildViewModel(aiProviderSettings = aiProviderSettings)
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { it.activeAiOperationLabel != null }
                assertEquals("Removing background…", state.activeAiOperationLabel)
            }
        }

    @Test
    fun `recent AI activity updates live while Home stays open, not only at first load`() =
        runTest {
            val aiProviderSettings = FakeAiProviderSettingsRepository()
            val viewModel = buildViewModel(aiProviderSettings = aiProviderSettings)
            viewModel.assistantState.test {
                val initial = awaitAssistantMatching { !it.isLoading }
                assertEquals(emptyList<AiActivityUiModel>(), initial.recentAiActivity)

                aiProviderSettings.activityFlow.value =
                    listOf(
                        AiActivityEntry(
                            capability = AiCapability.OUTFIT_STYLING,
                            provider = null,
                            outcome = AiCallOutcome.SUCCESS,
                            cacheHit = false,
                            timestamp = Instant.now(fixedClock),
                        ),
                    )
                val updated = awaitAssistantMatching { it.recentAiActivity.isNotEmpty() }
                assertEquals("Outfit styled", updated.recentAiActivity.first().headline)
            }
        }

    @Test
    fun `avatarImageUri is null, never a fabricated placeholder, when no photo has been set`() =
        runTest {
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals(null, state.avatarImageUri)
            }
        }

    @Test
    fun `avatarImageUri reflects the real saved profile photo path`() =
        runTest {
            val viewModel =
                buildViewModel(
                    personalization =
                        FakePersonalizationRepository(
                            PersonalizationSettings.DEFAULT.copy(avatarImageUri = "/data/profile/avatar.jpg"),
                        ),
                )
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertEquals("/data/profile/avatar.jpg", state.avatarImageUri)
            }
        }

    @Test
    fun `an unnamed user sees a plain greeting, never a fabricated name like User or Alex`() =
        runTest {
            val viewModel =
                buildViewModel(personalization = FakePersonalizationRepository(PersonalizationSettings.DEFAULT))
            viewModel.uiState.test {
                val state = awaitMatching { !it.isLoading }
                assertTrue(!state.greeting.contains("User"))
                assertTrue(!state.greeting.contains("null", ignoreCase = true))
            }
        }

    @Test
    fun `a failure while loading assistant state degrades to isLoading false, never crashes Home`() =
        runTest {
            val wardrobeIntelligence =
                FakeWardrobeIntelligenceRepository().apply {
                    buildDailyBriefError = RuntimeException("weather fetch failed")
                }
            val viewModel =
                HomeViewModel(
                    personalizationRepository = FakePersonalizationRepository(),
                    garmentRepository = FakeGarmentRepository(),
                    categoryRepository =
                        FakeCategoryRepository(listOf(Category(categoryId, "Tops", null, CategoryLevel.TOP))),
                    brandRepository = FakeBrandRepository(),
                    insightsRepositories =
                        HomeInsightsRepositories(
                            colorRepository = FakeColorRepository(),
                            outfitRepository = FakeOutfitRepository(),
                            wearEventRepository = FakeWearEventRepository(),
                            statsRepository = FakeStatsRepository(),
                            importQueueRepository = FakeImportQueueRepository(),
                        ),
                    assistantRepositories =
                        HomeAssistantRepositories(
                            FakeWeatherRepository(),
                            FakeStylingEngineRepository(),
                            FakeSyncRepository(),
                            wardrobeIntelligence,
                            FakeTripRepository(),
                            FakeAiProviderSettingsRepository(),
                        ),
                    clock = fixedClock,
                )
            viewModel.assistantState.test {
                val state = awaitAssistantMatching { !it.isLoading }
                assertEquals(null, state.weather)
                assertEquals(null, state.recommendation)
                assertEquals(null, state.wardrobeHealthScore)
                assertEquals(null, state.itemsNeedingAttentionCount)
            }
        }

    @Test
    fun `itemsNeedingAttentionCount is null before the assistant state resolves, never a fabricated zero`() =
        runTest {
            val viewModel = buildViewModel()
            assertEquals(null, viewModel.assistantState.value.itemsNeedingAttentionCount)
        }

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
