package com.wardrobe.app.feature.closet.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.AiProviderSettingsRepository
import com.wardrobe.app.core.domain.repository.BrandRepository
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.ImportQueueRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.profile.greetingText
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.headline
import com.wardrobe.app.core.model.weather.updatedAtLabel
import com.wardrobe.app.feature.closet.common.toTileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val RECENTLY_WORN_WINDOW_DAYS = 30L
private const val SYNC_CONFIRMATION_DURATION_MS = 4000L
private const val UPCOMING_WINDOW_DAYS = 30L

/** Groups Phase 7's Home-assistant dependencies so [HomeViewModel]'s own
 * constructor stays under detekt's `LongParameterList` threshold — the same
 * "bag of repositories" pattern `DeveloperPanelRepositories`/
 * `StylingContextDependencies` already established. Phase 9 added
 * [wardrobeIntelligenceRepository] (the Daily Brief/health-score/attention-
 * items source) and [tripRepository] (the upcoming-trip reminder) — both
 * on-demand, suspend-call dependencies, the same charter this bag already
 * had. */
class HomeAssistantRepositories
    @Inject
    constructor(
        val weatherRepository: WeatherRepository,
        val stylingEngineRepository: StylingEngineRepository,
        val syncRepository: SyncRepository,
        val wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
        val tripRepository: TripRepository,
        val aiProviderSettingsRepository: AiProviderSettingsRepository,
    )

/** Groups the repositories [HomeViewModel.uiState]'s own reactive `combine`
 * chain needs (as opposed to [HomeAssistantRepositories], which the Phase 7
 * assistant preview needs) — purely to keep the constructor's own parameter
 * count bounded, the same reasoning as [HomeAssistantRepositories]. */
class HomeInsightsRepositories
    @Inject
    constructor(
        val colorRepository: ColorRepository,
        val outfitRepository: OutfitRepository,
        val wearEventRepository: WearEventRepository,
        val statsRepository: StatsRepository,
        val importQueueRepository: ImportQueueRepository,
    )

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val personalizationRepository: PersonalizationRepository,
        private val garmentRepository: GarmentRepository,
        private val categoryRepository: CategoryRepository,
        private val brandRepository: BrandRepository,
        insightsRepositories: HomeInsightsRepositories,
        private val assistantRepositories: HomeAssistantRepositories,
        private val clock: Clock,
    ) : ViewModel() {
        private data class ReferenceData(
            val personalization: PersonalizationSettings,
            val garments: List<Garment>,
            val categories: List<Category>,
            val brands: List<Brand>,
        )

        private data class ActivityData(
            val usageStats: UsageStats,
            val wearEvents: List<WearEvent>,
            val incompleteImportCount: Int,
        )

        private data class InsightsSourceData(
            val colors: List<Color>,
            val outfits: List<Outfit>,
            val dormant: List<DormantItem>,
            val upcomingEvents: List<WearEvent>,
        )

        val uiState: StateFlow<HomeUiState> =
            combine(
                combine(
                    personalizationRepository.observe(),
                    garmentRepository.observeGarments(GarmentFilter(status = null)),
                    categoryRepository.observeAll(),
                    brandRepository.observeAll(),
                ) { personalization, garments, categories, brands ->
                    ReferenceData(personalization, garments, categories, brands)
                },
                combine(
                    insightsRepositories.statsRepository.observeUsageStats(StatsWindow.ONE_MONTH),
                    insightsRepositories.wearEventRepository.observeEvents(
                        DateRange(LocalDate.now(clock).minusDays(RECENTLY_WORN_WINDOW_DAYS), LocalDate.now(clock)),
                    ),
                    insightsRepositories.importQueueRepository.observeIncompleteCount(),
                ) { usageStats, wearEvents, incompleteImportCount ->
                    ActivityData(usageStats, wearEvents, incompleteImportCount)
                },
                combine(
                    insightsRepositories.colorRepository.observeAll(),
                    insightsRepositories.outfitRepository.observeOutfits(OutfitFilter()),
                    insightsRepositories.statsRepository.observeDormantItems(StatsWindow.ONE_MONTH),
                    insightsRepositories.wearEventRepository.observeEvents(
                        DateRange(LocalDate.now(clock), LocalDate.now(clock).plusDays(UPCOMING_WINDOW_DAYS)),
                    ),
                ) { colors, outfits, dormant, upcomingEvents ->
                    InsightsSourceData(colors, outfits, dormant, upcomingEvents)
                },
            ) { reference, activity, insightsSource -> buildUiState(reference, activity, insightsSource) }
                .catch { throwable ->
                    emit(HomeUiState(isLoading = false, error = throwable.message ?: "Something went wrong."))
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = HomeUiState(isLoading = true),
                )

        private val mutableAssistantState = MutableStateFlow(HomeAssistantUiState())

        /** Weather and a recommendation preview (Phase 7) — computed once,
         * not part of [uiState]'s own reactive `combine` chain: both are
         * on-demand suspend calls (a live/cached weather fetch, a full
         * recommendation-engine run), not `Flow`-backed data the rest of Home
         * already observes reactively. Neither ever blocks the rest of Home
         * from rendering — [assistantState] starts `isLoading = true` and the
         * page above renders regardless (Constitution rule 12). */
        val assistantState: StateFlow<HomeAssistantUiState> = mutableAssistantState.asStateFlow()

        init {
            viewModelScope.launch { loadAssistantState() }
            viewModelScope.launch { observeSyncCompletion() }
            viewModelScope.launch { observeRecentAiActivity() }
            viewModelScope.launch { observeActiveAiOperation() }
        }

        /** M18 — a live collection (not [loadAssistantState]'s one-shot
         * `.first()` reads) so a capability call that completes while Home
         * stays open updates this list without the user leaving and
         * reopening the screen. */
        private suspend fun observeRecentAiActivity() {
            assistantRepositories.aiProviderSettingsRepository
                .observeRecentActivity()
                .catch { /* M22: a transient read failure leaves the existing list as-is, never crashes Home. */ }
                .collect { entries ->
                    val now = Instant.now(clock)
                    val uiModels = entries.map { entry -> entry.toUiModel(now) }
                    mutableAssistantState.update { it.copy(recentAiActivity = uiModels) }
                }
        }

        /** M18's "is AI currently doing something" signal — a live
         * projection of `AiJobManager`'s own job ledger
         * ([com.wardrobe.app.core.model.ai.AiActiveOperation]), never a
         * fabricated "thinking" state. `null` the instant nothing is
         * genuinely `PENDING`/`RUNNING`. */
        private suspend fun observeActiveAiOperation() {
            assistantRepositories.aiProviderSettingsRepository
                .observeActiveOperations()
                .catch { /* M22: a transient read failure leaves the existing label as-is, never crashes Home. */ }
                .collect { operations ->
                    val label = operations.minByOrNull { it.startedAt }?.capability?.inProgressLabel()
                    mutableAssistantState.update { it.copy(activeAiOperationLabel = label) }
                }
        }

        /** Phase 8 — shows the plain-language "Wardrobe updated just now"
         * line for a few seconds whenever a background sync actually
         * changes something, then clears itself. [drop]\(1\) skips whatever
         * `lastSyncAt` already was before this screen opened — only a
         * *new* completion while Home is visible should surface this. */
        private suspend fun observeSyncCompletion() {
            assistantRepositories.syncRepository
                .observeStatus()
                .map { it.lastSyncAt }
                .distinctUntilChanged()
                .drop(1)
                .catch { /* M22: a transient read failure just skips this confirmation, never crashes Home. */ }
                .collect {
                    mutableAssistantState.update { state ->
                        state.copy(syncConfirmationMessage = "Wardrobe updated just now")
                    }
                    delay(SYNC_CONFIRMATION_DURATION_MS)
                    mutableAssistantState.update { state -> state.copy(syncConfirmationMessage = null) }
                }
        }

        /** Phase 9 — the Daily Wardrobe Brief. [wardrobeIntelligenceRepository]'s
         * `buildDailyBrief` already fetches weather and generates today's
         * recommendation internally (reused for both this screen's weather
         * line and recommendation preview, rather than fetching weather
         * twice); the health score, attention-item count, and trip reminder
         * are separate, cheap on-demand reads alongside it.
         *
         * M22 fix: this whole body used to run with no error handling at
         * all — a single throwing suspend call anywhere in this chain (e.g.
         * `buildDailyBrief`'s weather fetch) would leave an unhandled
         * exception in a bare `viewModelScope.launch`, which crashes the app
         * rather than just leaving Home's assistant cards absent. Every real
         * failure now degrades to "stop loading, show nothing new" — never a
         * fabricated value, matching every card's own existing null-means-
         * absent contract. */
        private suspend fun loadAssistantState() {
            try {
                loadAssistantStateOrThrow()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableAssistantState.update { it.copy(isLoading = false) }
            }
        }

        private suspend fun loadAssistantStateOrThrow() {
            val personalization = personalizationRepository.observe().first()
            val today = LocalDate.now(clock)
            val greeting = if (personalization.showGreeting) personalization.greetingText(LocalTime.now(clock)) else ""
            val wardrobeIntelligence = assistantRepositories.wardrobeIntelligenceRepository
            val dailyBrief =
                if (personalization.showWeatherCard || personalization.showRecommendationCard) {
                    wardrobeIntelligence.buildDailyBrief(today, greeting)
                } else {
                    null
                }
            val weatherCard =
                if (personalization.showWeatherCard) {
                    dailyBrief?.weather?.let { snapshot ->
                        WeatherCardUiModel(
                            headline = snapshot.headline(TemperatureUnit.CELSIUS),
                            updatedAtLabel = snapshot.updatedAtLabel(Instant.now(clock)),
                        )
                    }
                } else {
                    null
                }
            val recommendation =
                if (personalization.showRecommendationCard) {
                    dailyBrief?.recommendation?.let { buildRecommendationPreview(it) }
                } else {
                    null
                }
            val healthScore = wardrobeIntelligence.observeWardrobeHealthScore().first()
            val aiConfigs = assistantRepositories.aiProviderSettingsRepository.observeAllConfigs().first()
            mutableAssistantState.update {
                it.copy(
                    isLoading = false,
                    weather = weatherCard,
                    recommendation = recommendation,
                    todaysOccasionName = dailyBrief?.todaysOccasionName,
                    wardrobeHealthScore = healthScore.score,
                    rotationScore = healthScore.rotationScore,
                    itemsNeedingAttentionCount = attentionItemCount(wardrobeIntelligence),
                    upcomingTripReminder = upcomingTripReminder(today),
                    laundryReminderCount =
                        garmentRepository
                            .observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE))
                            .first()
                            .count { it.isInLaundry },
                    cloudAiConfiguredCount = aiConfigs.count { it.isCloudReady() },
                    totalAiCapabilities = aiConfigs.size,
                )
            }
        }

        private suspend fun buildRecommendationPreview(scored: ScoredOutfit): RecommendationPreviewUiModel {
            val garmentsById =
                garmentRepository.observeGarments(GarmentFilter(status = null)).first().associateBy { it.id }
            val categoriesById = categoryRepository.observeAll().first().associateBy { it.id }
            val brandsById = brandRepository.observeAll().first().associateBy { it.id }
            val items =
                scored.outfit.garments.mapNotNull { slot ->
                    garmentsById[slot.garmentId]?.toTileUiModel(categoriesById, brandsById)
                }
            return RecommendationPreviewUiModel(items = items, explanation = scored.explanation)
        }

        private suspend fun attentionItemCount(wardrobeIntelligence: WardrobeIntelligenceRepository): Int {
            val alerts = wardrobeIntelligence.observeWardrobeAlerts().first()
            return alerts.forgotten.size + alerts.overused.size + alerts.neverWorn.size
        }

        private suspend fun upcomingTripReminder(today: LocalDate): String? {
            val upcoming =
                assistantRepositories.tripRepository
                    .observeTrips()
                    .first()
                    .filter { !today.isAfter(it.dateRange.start) }
                    .minByOrNull { it.dateRange.start } ?: return null
            val daysUntil = ChronoUnit.DAYS.between(today, upcoming.dateRange.start)
            val name = upcoming.name ?: upcoming.destination
            return when {
                daysUntil <= 0 -> "Your trip to $name starts today"
                daysUntil == 1L -> "Your trip to $name starts tomorrow"
                else -> "Your trip to $name is in $daysUntil days"
            }
        }

        private fun buildUiState(
            reference: ReferenceData,
            activity: ActivityData,
            insightsSource: InsightsSourceData,
        ): HomeUiState {
            val categoriesById = reference.categories.associateBy { it.id }
            val brandsById = reference.brands.associateBy { it.id }
            val activeGarments = reference.garments.filter { it.status == GarmentStatus.ACTIVE }
            val garmentsById = reference.garments.associateBy { it.id }
            val personalization = reference.personalization
            val lookups =
                ReferenceLookups(
                    garmentsById = garmentsById,
                    categoriesById = categoriesById,
                    brandsById = brandsById,
                    colorsById = insightsSource.colors.associateBy { it.id },
                    outfitsById = insightsSource.outfits.associateBy { it.id },
                )

            val sections =
                buildGarmentSections(activeGarments, activity.wearEvents, garmentsById, categoriesById, brandsById)

            return HomeUiState(
                isLoading = false,
                greeting = if (personalization.showGreeting) personalization.greetingText(LocalTime.now(clock)) else "",
                homeTitle = personalization.customHomeTitle?.takeUnless { it.isBlank() } ?: "My Wardrobe",
                showGreeting = personalization.showGreeting,
                showWardrobeHealthCard = personalization.showWardrobeHealthCard,
                avatarImageUri = personalization.avatarImageUri,
                currentDateText = LocalDate.now(clock).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                summary =
                    WardrobeSummaryUiModel(
                        totalActiveGarments = activity.usageStats.totalActiveGarments,
                        wornAtLeastOnce = activity.usageStats.wornAtLeastOnce,
                        usagePercent = activity.usageStats.usagePercent.toInt(),
                    ),
                recentlyAdded = sections.recentlyAdded,
                recentlyWorn = sections.recentlyWorn,
                continueEditing = sections.continueEditing,
                incompleteImportCount = activity.incompleteImportCount,
                insights =
                    buildHomeInsights(
                        usageStats = activity.usageStats,
                        lookups = lookups,
                        dormant = insightsSource.dormant,
                        upcomingEvents = insightsSource.upcomingEvents,
                        today = LocalDate.now(clock),
                    ),
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5000L
        }
    }
