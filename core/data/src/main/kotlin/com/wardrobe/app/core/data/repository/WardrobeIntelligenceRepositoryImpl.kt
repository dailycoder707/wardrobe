package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.FeedbackDao
import com.wardrobe.app.core.database.dao.StatsDao
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.intelligence.CapsuleSuggestion
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.model.intelligence.DailyBrief
import com.wardrobe.app.core.model.intelligence.DuplicateGroup
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.ShoppingGapSuggestion
import com.wardrobe.app.core.model.intelligence.WardrobeAlerts
import com.wardrobe.app.core.model.intelligence.WardrobeHealthScore
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.wear.WearEventStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Phase 9 — the two small Room-level dependencies
 * [WardrobeIntelligenceRepositoryImpl] needs beyond the domain repositories
 * every other phase already exposes. */
class WardrobeIntelligenceDaos
    @Inject
    constructor(
        val statsDao: StatsDao,
        val feedbackDao: FeedbackDao,
    )

/** Phase 9 — every domain repository [WardrobeIntelligenceRepositoryImpl]
 * composes, bundled to stay under detekt's `LongParameterList` threshold —
 * the established "bag of repositories" pattern. */
class WardrobeIntelligenceRepositories
    @Inject
    constructor(
        val garmentRepository: GarmentRepository,
        val outfitRepository: OutfitRepository,
        val categoryRepository: CategoryRepository,
        val occasionRepository: OccasionRepository,
        val statsRepository: StatsRepository,
        val wearEventRepository: WearEventRepository,
        val tripRepository: TripRepository,
        val stylingEngineRepository: StylingEngineRepository,
        val weatherRepository: WeatherRepository,
    )

/**
 * Phase 9 — every "smart wardrobe" derived insight, composed entirely from
 * data `GarmentRepository`/`OutfitRepository`/`WearEventRepository`/
 * `TripRepository`/`StatsDao`/`FeedbackDao` already own. Nothing here is a
 * new source of truth — this class stays a thin orchestrator; the actual
 * computation lives in the sibling `*Builders.kt` files in this package
 * (split out purely to stay under detekt's `TooManyFunctions` ceiling, the
 * same "class stays thin, top-level functions do the work" split
 * `StatsRepositoryImpl`/`OutfitAssembler` already established).
 */
class WardrobeIntelligenceRepositoryImpl
    @Inject
    constructor(
        private val daos: WardrobeIntelligenceDaos,
        private val repositories: WardrobeIntelligenceRepositories,
        private val clock: Clock,
    ) : WardrobeIntelligenceRepository {
        override fun observeGarmentInsights(garmentId: GarmentId): Flow<GarmentInsights?> =
            combine(
                repositories.garmentRepository.observeGarment(garmentId),
                daos.statsDao.observeWearDatesForGarment(garmentId.value),
                packedTripNameFlow(repositories.tripRepository, garmentId, clock),
            ) { garment, wearDateStrings, packedForTripName ->
                garment?.let { buildGarmentInsights(it, wearDateStrings, packedForTripName, clock) }
            }

        override fun observeOutfitInsights(outfitId: OutfitId): Flow<OutfitInsights?> =
            combine(
                repositories.outfitRepository.observeOutfit(outfitId),
                repositories.wearEventRepository.observeEvents(allTimeRange(clock)),
                daos.feedbackDao.observeVoteCountsForOutfit(outfitId.value),
                repositories.garmentRepository.observeGarments(GarmentFilter()),
                repositories.occasionRepository.observeAll(),
            ) { outfit, wearEvents, voteCounts, allGarments, occasions ->
                outfit?.let { buildOutfitInsights(it, wearEvents, voteCounts, allGarments, occasions, clock) }
            }

        override fun observeWardrobeAlerts(): Flow<WardrobeAlerts> =
            combine(
                daos.statsDao.observeDormantSince(dormantCutoff(clock)),
                daos.statsDao.observeCostPerWear(),
                repositories.garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
            ) { dormantRows, costRows, garments ->
                WardrobeAlerts(
                    forgotten = buildForgottenGarments(dormantRows, clock),
                    overused = buildOverusedGarments(costRows),
                    neverWorn = buildNeverWornGarments(costRows, garments, clock),
                )
            }

        override fun observeShoppingGaps(): Flow<List<ShoppingGapSuggestion>> =
            combine(
                daos.statsDao.observeActiveGarmentCountByCategory(),
                repositories.categoryRepository.observeAll(),
            ) { countRows, categories ->
                buildShoppingGaps(countRows, categories.associateBy { it.id.value })
            }

        override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> =
            combine(
                repositories.garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
                daos.statsDao.observeCostPerWear(),
            ) { garments, costRows -> buildDuplicateGroups(garments, costRows) }

        override suspend fun suggestCapsule(type: CapsuleType): CapsuleSuggestion {
            val activeFilter = GarmentFilter(status = GarmentStatus.ACTIVE)
            val garments = repositories.garmentRepository.observeGarments(activeFilter).first()
            val categories =
                repositories.categoryRepository
                    .observeAll()
                    .first()
                    .associateBy { it.id }
            val costPerWear =
                repositories.statsRepository
                    .observeCostPerWear()
                    .first()
                    .associateBy { it.garmentId }
            return generateCapsule(type, garments, categories, costPerWear)
        }

        override fun observeWardrobeHealthScore(): Flow<WardrobeHealthScore> =
            combine(
                repositories.statsRepository.observeUsageStats(StatsWindow.SIX_MONTHS),
                daos.statsDao.observeCostPerWear(),
                daos.statsDao.observeDormantSince(dormantCutoff(clock)),
            ) { usage, costRows, dormantRows ->
                val forgottenCount = buildForgottenGarments(dormantRows, clock).size
                buildWardrobeHealthScore(usage, costRows.map { it.wearCount }, forgottenCount)
            }

        override fun observeCalendarConflicts(lookAheadDays: Int): Flow<List<CalendarConflict>> =
            combine(
                repositories.wearEventRepository.observeEvents(lookAheadRange(lookAheadDays, clock)),
                repositories.garmentRepository.observeGarments(GarmentFilter()),
            ) { events, garments -> events to garments }
                .map { (events, garments) -> buildCalendarConflicts(events, garments, repositories.tripRepository) }

        override suspend fun buildDailyBrief(
            today: LocalDate,
            greeting: String,
        ): DailyBrief {
            val weather = repositories.weatherRepository.getForecastForConfiguredLocation(today)
            val plannedToday =
                repositories.wearEventRepository
                    .observeEvents(DateRange(today, today))
                    .first()
                    .firstOrNull { it.status == WearEventStatus.PLANNED && it.occasionId != null }
            val occasionName =
                plannedToday?.occasionId?.let { occasionId ->
                    repositories.occasionRepository
                        .observeAll()
                        .first()
                        .firstOrNull { it.id == occasionId }
                        ?.name
                }
            val context = SuggestionContext(date = today, weather = weather, occasionId = plannedToday?.occasionId)
            val recommendation = repositories.stylingEngineRepository.suggestOutfits(context).firstOrNull()
            val explanations =
                buildList {
                    recommendation?.explanation?.let(::add)
                    recommendation?.accessoryItems?.forEach { add(it.explanation) }
                    recommendation?.jewelryItems?.forEach { add(it.explanation) }
                }
            return DailyBrief(greeting, weather, occasionName, recommendation, explanations)
        }
    }
