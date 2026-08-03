package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.data.repository.styling.countAppliedAvoidRules
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.outfit.impliedDressCode
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.styling.WeatherSource
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Top-level, `StylingEngineRepositoryImpl`-only helpers for Phase 7's
 * context resolution — pulled out of that class specifically to keep its
 * own function count under detekt's `TooManyFunctions` threshold, the same
 * "class stays a thin orchestrator, top-level functions do the work" split
 * the sibling `styling` package already uses. */
internal const val PLANNED_OUTFIT_SCORE = 100.0
private const val LIMITED_CHOICES_PACKED_FRACTION = 0.5

/** [SuggestionContext.weather], if the caller already knows it (e.g. a
 * future trip day whose forecast was already fetched), always wins;
 * otherwise resolves live weather for [suggestionContext]'s date via
 * [WeatherRepository.getForecastForConfiguredLocation] — never throws,
 * never blocks longer than a cache read, and returns `null` on its own when
 * Weather Settings says not to use weather or no location is resolvable. */
internal suspend fun resolveWeather(
    weatherRepository: WeatherRepository,
    suggestionContext: SuggestionContext,
): WeatherSnapshot? =
    suggestionContext.weather ?: weatherRepository.getForecastForConfiguredLocation(suggestionContext.date)

/** Today's planned calendar occasion, if any, mapped to the dress code it
 * typically implies (`Occasion.impliedDressCode`, `core:model`) — `null`
 * when nothing is planned or the occasion's name doesn't map to a
 * recognizable dress code. */
internal suspend fun resolvePlannedOccasionDressCode(
    wearEventRepository: WearEventRepository,
    occasionRepository: OccasionRepository,
    date: LocalDate,
): DressCode? {
    val occasionId =
        wearEventRepository
            .observeEvents(DateRange(date, date))
            .first()
            .firstOrNull { it.status == WearEventStatus.PLANNED && it.occasionId != null }
            ?.occasionId
    val occasion = occasionId?.let { id -> occasionRepository.observeAll().first().firstOrNull { it.id == id } }
    return occasion?.impliedDressCode()
}

internal data class PlannedOutfitResolution(
    val outfits: List<ScoredOutfit>,
    val plannedOutfitUsed: Boolean,
)

/** If today already has a planned, complete outfit (Calendar), it's
 * surfaced first with its own plain-language explanation — "context
 * refines, never replaces" (Constitution rule 12): the freshly generated
 * suggestions still follow it, they're just not first. */
internal suspend fun prependPlannedOutfit(
    wearEventRepository: WearEventRepository,
    outfitRepository: OutfitRepository,
    date: LocalDate,
    generated: List<ScoredOutfit>,
): PlannedOutfitResolution {
    val plannedOutfitId =
        wearEventRepository
            .observeEvents(DateRange(date, date))
            .first()
            .firstOrNull { it.status == WearEventStatus.PLANNED && it.outfitId != null }
            ?.outfitId
    val plannedOutfit = plannedOutfitId?.let { outfitRepository.getOutfit(it) }
    return if (plannedOutfit != null) {
        val entry =
            ScoredOutfit(
                outfit = plannedOutfit,
                score = PLANNED_OUTFIT_SCORE,
                explanation = "You already planned this outfit for today.",
                passedWeatherFilter = true,
            )
        PlannedOutfitResolution(listOf(entry) + generated, true)
    } else {
        PlannedOutfitResolution(generated, false)
    }
}

/** Prepends any whole-recommendation context notes (e.g. "many items are
 * packed for a trip") to every outfit's explanation — a no-op when there
 * are none. */
internal fun withContextNotes(
    outfits: List<ScoredOutfit>,
    notes: List<String>,
): List<ScoredOutfit> {
    if (notes.isEmpty()) return outfits
    val prefix = notes.joinToString(" ")
    return outfits.map { it.copy(explanation = "$prefix ${it.explanation}") }
}

/** Builds the Developer Panel snapshot for the run that produced [input] —
 * pure, so `StylingEngineRepositoryImplTest` can assert against it directly
 * without needing to inspect private state. */
internal fun buildRunDiagnostics(
    input: EngineInput,
    totalActiveCount: Int,
    awayOnTrip: Set<GarmentId>,
    now: Instant,
): RecommendationRunDiagnostics {
    val weather = input.weather
    val weatherSource =
        when {
            weather == null -> WeatherSource.NONE
            weather.isStale -> WeatherSource.CACHED
            else -> WeatherSource.LIVE
        }
    val cacheAgeMinutes = weather?.let { Duration.between(it.fetchedAt, now).toMinutes() }
    val contextNotes = mutableListOf<String>()
    if (totalActiveCount > 0 && awayOnTrip.size.toDouble() / totalActiveCount >= LIMITED_CHOICES_PACKED_FRACTION) {
        contextNotes += "Many of your items are packed for a trip, so today's choices are more limited."
    }
    return RecommendationRunDiagnostics(
        weatherSource = weatherSource,
        weatherCacheAgeMinutes = cacheAgeMinutes,
        rulesEvaluatedCount = input.activeRules.size,
        rulesAppliedCount = countAppliedAvoidRules(input),
        plannedOutfitUsed = false,
        contextNotes = contextNotes,
    )
}
