package com.wardrobe.app.feature.calendar.calendar

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.core.model.weather.dailyHeadline
import com.wardrobe.app.core.model.weather.headline
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

private const val CONFIDENCE_PERCENT_MULTIPLIER = 100

/** M20 — how far back "worn recently" looks, relative to today, regardless
 * of which calendar date is selected (Part 12: a real signal to help decide
 * *today*, not a per-day-relative one). */
internal const val RECENT_WEAR_WINDOW_DAYS = 14L

/** Mirrors M19's exact "Show another" exhaustion wording
 * (`feature:outfits`' `NO_MORE_ALTERNATIVES_MESSAGE`) so the same honest
 * behavior reads identically wherever it appears — not imported directly
 * since feature modules don't cross-depend (ADR-010). */
internal const val NO_MORE_ALTERNATIVES_MESSAGE =
    "No other complete outfit matches this context with your current wardrobe."

internal fun ScoredOutfit.toDayRecommendationUiModel(garmentsById: Map<GarmentId, Garment>): DayRecommendationUiModel {
    val thumbnails =
        outfit.garments.sortedBy { it.layerSlot }.map { slot ->
            garmentsById[slot.garmentId]?.images?.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
        }
    return DayRecommendationUiModel(
        title = outfit.name?.takeUnless { it.isBlank() } ?: "Suggested outfit",
        explanation = explanation,
        thumbnails = thumbnails,
        providerLabel = provenance?.provider,
        confidencePercent = aiConfidence?.let { (it * CONFIDENCE_PERCENT_MULTIPLIER).toInt() },
        isCacheHit = provenance?.cacheHit == true,
    )
}

internal fun ScoredOutfit.garmentSignature(): Set<GarmentId> = outfit.garments.map { it.garmentId }.toSet()

/** Part 8's three honestly-distinct states — see [DateWeatherUiState]'s own
 * KDoc for what each one means and why a date mismatch (not just a `null`
 * snapshot) is treated as its own case. */
internal fun resolveDateWeather(
    snapshot: WeatherSnapshot?,
    requestedDate: LocalDate,
    today: LocalDate,
    unit: TemperatureUnit,
): DateWeatherUiState =
    when {
        snapshot == null -> DateWeatherUiState.Unavailable
        snapshot.date != requestedDate -> DateWeatherUiState.ForecastUnavailableForDate
        requestedDate == today -> DateWeatherUiState.Available(snapshot.headline(unit))
        else -> DateWeatherUiState.Available(snapshot.dailyHeadline(unit))
    }

/** Bundles what's being planned (as opposed to [CalendarRecommendationDependencies]'
 * "how to persist it") so [persistDayRecommendation]'s own parameter count
 * stays under detekt's `LongParameterList` threshold. */
internal data class DayPlanRequest(
    val scored: ScoredOutfit,
    val date: LocalDate,
    val occasionId: OccasionId?,
    val replacingEvent: WearEvent?,
)

/** Persists a recommended outfit the same way `feature:outfits`' own
 * "schedule for a date" quick action does (`persistSelectedOutfit`/
 * `logOutfitWear`, M19) — a fresh AI/rule suggestion carries the
 * `OutfitId(0)` sentinel until this saves it for real. [DayPlanRequest.replacingEvent],
 * when non-null, reuses that existing row's id/note/weatherCacheId/
 * createdAt via `.copy(...)` rather than deleting and re-inserting — this
 * is Part 11's "Replace", not a duplicate plan. */
internal suspend fun persistDayRecommendation(
    dependencies: CalendarRecommendationDependencies,
    request: DayPlanRequest,
    today: LocalDate,
    clock: Clock,
) {
    val scored = request.scored
    val date = request.date
    val occasionId = request.occasionId
    val replacingEvent = request.replacingEvent
    val outfitId =
        if (scored.outfit.id.value != 0L) {
            scored.outfit.id
        } else {
            dependencies.outfitRepository.saveOutfit(scored.outfit.copy(isSaved = true))
        }
    // Mirrors `CalendarEventActions.onRescheduleEvent`'s exact status rule:
    // a date that isn't in the future only forces WORN for a brand-new plan.
    // Replacing a still-PLANNED "today" event (planned a few days ago, not
    // yet confirmed worn) must not silently mark it worn just because it's
    // being replaced — that's the reschedule path's job, not this one's.
    val status =
        when {
            date.isAfter(today) -> WearEventStatus.PLANNED
            replacingEvent != null -> replacingEvent.status
            else -> WearEventStatus.WORN
        }
    if (replacingEvent != null) {
        dependencies.wearEventRepository.updateWear(
            replacingEvent.copy(outfitId = outfitId, garmentId = null, occasionId = occasionId, status = status),
        )
    } else {
        dependencies.wearEventRepository.logWear(
            WearEvent(
                id = WearEventId(0),
                date = date,
                garmentId = null,
                outfitId = outfitId,
                weatherCacheId = null,
                occasionId = occasionId,
                note = null,
                status = status,
                createdAt = Instant.now(clock),
            ),
        )
    }
}

/** `null` when there's nothing to check (no garment resolved, e.g. a
 * reference to a deleted item) or when [com.wardrobe.app.core.domain.repository.StatsRepository]
 * has no wear-count entry for any of them at all — never guessed as "not
 * worn recently" just because data happens to be missing. Only computed for
 * [WearEventStatus.PLANNED] events; a [WearEventStatus.WORN] entry *is* the
 * wear record, so the question doesn't add information there. */
internal fun computeWornRecently(
    event: WearEvent,
    outfitsById: Map<OutfitId, Outfit>,
    costPerWearByGarmentId: Map<GarmentId, CostPerWearEntry>,
    today: LocalDate,
): Boolean? {
    if (event.status != WearEventStatus.PLANNED) return null
    val garmentIds =
        when {
            event.garmentId != null -> listOf(event.garmentId)
            event.outfitId != null -> outfitsById[event.outfitId]?.garments?.map { it.garmentId }.orEmpty()
            else -> emptyList()
        }
    val entries = garmentIds.mapNotNull { costPerWearByGarmentId[it] }
    val cutoff = today.minusDays(RECENT_WEAR_WINDOW_DAYS)
    return entries
        .takeIf { it.isNotEmpty() }
        ?.any { entry -> entry.lastWornDate?.let { lastWornDate -> !lastWornDate.isBefore(cutoff) } == true }
}
