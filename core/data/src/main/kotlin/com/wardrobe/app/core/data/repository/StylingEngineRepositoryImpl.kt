package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.data.repository.styling.buildSlotCandidates
import com.wardrobe.app.core.data.repository.styling.generateRecommendations
import com.wardrobe.app.core.data.repository.styling.passesWeatherFilter
import com.wardrobe.app.core.data.repository.styling.scoreCandidate
import com.wardrobe.app.core.domain.repository.CategoryRepository
import com.wardrobe.app.core.domain.repository.ColorRepository
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StyleRuleRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.StylistPreferencesRepository
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.garmentsBySlot
import com.wardrobe.app.core.model.stats.StatsWindow
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val DEFAULT_SUGGESTION_COUNT = 3

/** Groups Phase 7's context-refinement dependencies so
 * [StylingEngineRepositoryImpl]'s own constructor stays under detekt's
 * `LongParameterList` threshold — the same "bag of repositories" pattern
 * `DeveloperPanelRepositories` (`feature:closet`) already established. */
class StylingContextDependencies
    @Inject
    constructor(
        val weatherRepository: WeatherRepository,
        val wearEventRepository: WearEventRepository,
        val outfitRepository: OutfitRepository,
        val occasionRepository: OccasionRepository,
    )

/**
 * The Phase 6 rule engine's public entry point — the actual scoring/assembly
 * logic lives in the sibling `styling` package (`buildSlotCandidates`,
 * `scoreCandidate`, `generateRecommendations`, `buildExplanation`), following
 * the same "class stays a thin orchestrator, top-level functions do the work"
 * split `StatsRepositoryImpl` used in Phase 5e — keeps this class's own
 * function/line counts small regardless of how the algorithm grows.
 *
 * Both interface methods are `suspend fun`, not `Flow`-returning: a suggestion
 * is generated on demand (the "Generate Another Look" quick action), not
 * continuously recomputed as the wardrobe changes underneath it — the caller
 * (ViewModel) holds the result in its own state until the user asks again.
 *
 * Phase 7 added weather/calendar context resolution, entirely inside
 * [loadEngineInput] — the same place trip/laundry/availability already
 * resolve (Phase 6). Every context source is best-effort and `null`-safe
 * (Constitution rule 12, Context Refinement Rule): a call with no weather
 * data, no calendar plan, and no active trip still produces complete outfits,
 * exactly like Phase 6 did before this phase existed. The actual context-
 * resolution functions (`resolveWeather`, `resolvePlannedOccasionDressCode`,
 * `prependPlannedOutfit`, `withContextNotes`, `buildRunDiagnostics`) live in
 * the sibling `ContextResolution.kt` file, for the same "keep this class's
 * own function count small" reason.
 */
class StylingEngineRepositoryImpl
    @Inject
    constructor(
        private val garmentRepository: GarmentRepository,
        private val categoryRepository: CategoryRepository,
        private val colorRepository: ColorRepository,
        private val styleRuleRepository: StyleRuleRepository,
        private val stylistPreferencesRepository: StylistPreferencesRepository,
        private val tripRepository: TripRepository,
        private val statsRepository: StatsRepository,
        private val contextDependencies: StylingContextDependencies,
        private val clock: Clock,
    ) : StylingEngineRepository {
        @Volatile
        private var runDiagnostics = RecommendationRunDiagnostics()

        override suspend fun suggestOutfits(context: SuggestionContext): List<ScoredOutfit> {
            val input = loadEngineInput(context)
            val generated = generateRecommendations(input, DEFAULT_SUGGESTION_COUNT)
            val resolution =
                prependPlannedOutfit(
                    contextDependencies.wearEventRepository,
                    contextDependencies.outfitRepository,
                    context.date,
                    generated,
                )
            if (resolution.plannedOutfitUsed) runDiagnostics = runDiagnostics.copy(plannedOutfitUsed = true)
            return withContextNotes(resolution.outfits, runDiagnostics.contextNotes)
        }

        override suspend fun suggestForItem(
            garmentId: GarmentId,
            context: SuggestionContext,
        ): List<ScoredOutfit> {
            val input = loadEngineInput(context)
            val anchor = input.garmentsById[garmentId] ?: return emptyList()
            val generated = generateRecommendations(input, DEFAULT_SUGGESTION_COUNT, anchor)
            return withContextNotes(generated, runDiagnostics.contextNotes)
        }

        override suspend fun suggestReplacementForSlot(
            outfit: Outfit,
            slot: OutfitSlot,
            context: SuggestionContext,
        ): GarmentId? {
            val input = loadEngineInput(context)
            val currentGarmentId = outfit.garmentsBySlot()[slot]
            val candidates = buildSlotCandidates(input)[slot].orEmpty().filter { it.id != currentGarmentId }
            return bestReplacement(candidates, input)
        }

        /** Phase 9 — the [AccessoryCategory]/[JewelryCategory] equivalent of
         * [suggestReplacementForSlot], needed because `OutfitSlot.ACCESSORIES`/
         * `JEWELRY` can no longer disambiguate "replace just the ring" from
         * "replace just the necklace" now that a slot can carry several
         * concurrent presentation-only picks. */
        override suspend fun suggestReplacementForAccessory(
            outfit: Outfit,
            category: AccessoryCategory,
            excludingGarmentId: GarmentId,
            context: SuggestionContext,
        ): GarmentId? {
            val input = loadEngineInput(context)
            val candidates =
                buildSlotCandidates(input)[OutfitSlot.ACCESSORIES]
                    .orEmpty()
                    .filter { it.id != excludingGarmentId && it.accessoryCategory(input) == category }
            return bestReplacement(candidates, input)
        }

        override suspend fun suggestReplacementForJewelry(
            outfit: Outfit,
            category: JewelryCategory,
            excludingGarmentId: GarmentId,
            context: SuggestionContext,
        ): GarmentId? {
            val input = loadEngineInput(context)
            val candidates =
                buildSlotCandidates(input)[OutfitSlot.JEWELRY]
                    .orEmpty()
                    .filter { it.id != excludingGarmentId && it.jewelryCategory(input) == category }
            return bestReplacement(candidates, input)
        }

        override fun lastRunDiagnostics(): RecommendationRunDiagnostics = runDiagnostics

        private suspend fun loadEngineInput(suggestionContext: SuggestionContext): EngineInput {
            val today = LocalDate.now(clock)
            val allActive = garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)).first()
            val awayOnTrip = currentlyPackedGarmentIds(today)
            val candidateGarments = allActive.filter { !it.isInLaundry && it.id !in awayOnTrip }

            val categories = categoryRepository.observeAll().first()
            val colors = colorRepository.observeAll().first()
            val costPerWear = statsRepository.observeCostPerWear().first()
            val usage = statsRepository.observeUsageStats(StatsWindow.SIX_MONTHS).first()
            val preferences = stylistPreferencesRepository.observePreferences().first()
            val activeRules = styleRuleRepository.observeActiveRules().first()
            val weather = resolveWeather(contextDependencies.weatherRepository, suggestionContext)
            val plannedOccasionDressCode =
                resolvePlannedOccasionDressCode(
                    contextDependencies.wearEventRepository,
                    contextDependencies.occasionRepository,
                    suggestionContext.date,
                )

            val input =
                EngineInput(
                    candidateGarments = candidateGarments,
                    garmentsById = allActive.associateBy { it.id },
                    categoriesById = categories.associateBy { it.id },
                    colorsById = colors.associateBy { it.id },
                    activeRules = activeRules,
                    preferences = preferences,
                    costPerWearByGarmentId = costPerWear.associateBy { it.garmentId },
                    favoriteColorIds = usage.signatureColorIds,
                    today = today,
                    weather = weather,
                    plannedOccasionDressCode = plannedOccasionDressCode,
                )
            runDiagnostics = buildRunDiagnostics(input, allActive.size, awayOnTrip, clock.instant())
            return input
        }

        /** A garment counts as "away" only when it's packed (not merely listed) for
         * a trip whose date range includes today — see
         * phase-6-personal-wardrobe-stylist.md's Trip Status derivation. */
        private suspend fun currentlyPackedGarmentIds(today: LocalDate): Set<GarmentId> {
            val activeTrip = tripRepository.observeTrips().first().firstOrNull { today in it.dateRange }
            val activeTripId = activeTrip?.id ?: return emptySet()
            return tripRepository
                .observePackingList(activeTripId)
                .first()
                .filter { it.isPacked }
                .mapNotNull { it.garmentId }
                .toSet()
        }
    }

private operator fun DateRange.contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

/** Smart-completion for a single-item replacement: tries the weather-safe
 * subset first, falling back to every candidate only when the weather
 * filter would otherwise leave nothing at all — the same "never leave it
 * empty just because of weather" rule `OutfitAssembler.pickNthSmart`
 * applies during initial generation. Kept as a top-level (not class member)
 * function, alongside [accessoryCategory]/[jewelryCategory] below, purely
 * to stay under detekt's `TooManyFunctions` ceiling on
 * [StylingEngineRepositoryImpl]. */
private fun bestReplacement(
    candidates: List<Garment>,
    input: EngineInput,
): GarmentId? {
    val weatherSafe = candidates.filter { passesWeatherFilter(it, input.weather) }
    return weatherSafe.ifEmpty { candidates }.maxByOrNull { scoreCandidate(it, input).score }?.id
}

private fun Garment.accessoryCategory(input: EngineInput): AccessoryCategory? =
    input.categoriesById[categoryId]?.name?.let(AccessoryCategory::classify)

private fun Garment.jewelryCategory(input: EngineInput): JewelryCategory? =
    input.categoriesById[categoryId]?.name?.let(JewelryCategory::classify)
