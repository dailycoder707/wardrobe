package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.FeedbackVoteCountRow
import com.wardrobe.app.core.domain.repository.TripRepository
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.intelligence.ComfortLevel
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.OutfitRating
import com.wardrobe.app.core.model.intelligence.WarmthLevel
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.impliedDressCode
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.toMeteorologicalSeason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Phase 9 — [WardrobeIntelligenceRepositoryImpl]'s per-garment/per-outfit
 * insight builders, split into their own file (rather than left as private
 * class members) purely to stay under detekt's `TooManyFunctions` ceiling —
 * the class itself stays a thin orchestrator, mirroring the split already
 * used for `OutfitAssembler`/`StylingEngineRepositoryImpl`. */

internal fun buildGarmentInsights(
    garment: Garment,
    wearDateStrings: List<String>,
    packedForTripName: String?,
    clock: Clock,
): GarmentInsights {
    val today = LocalDate.now(clock)
    val wearDates = wearDateStrings.map(LocalDate::parse).sorted()
    val totalWears = wearDates.size
    val firstWorn = wearDates.firstOrNull()
    val lastWorn = wearDates.lastOrNull()
    val averageDaysBetweenWears = averageInterval(wearDates)
    val costPerWear =
        garment.price
            ?.amount
            ?.takeIf { totalWears > 0 }
            ?.let { it / totalWears }
    return GarmentInsights(
        garmentId = garment.id,
        lastWornDate = lastWorn,
        firstWornDate = firstWorn,
        totalWears = totalWears,
        averageDaysBetweenWears = averageDaysBetweenWears,
        wearFrequencyPerMonth = wearFrequency(firstWorn, totalWears, today),
        rotationScore = rotationScoreFor(lastWorn, averageDaysBetweenWears, today),
        seasonUsage = wearDates.groupingBy { it.toMeteorologicalSeason() }.eachCount(),
        costPerWear = costPerWear,
        isFavorite = garment.isFavorite,
        status = garment.status,
        isInLaundry = garment.isInLaundry,
        packedForTripName = packedForTripName,
    )
}

internal fun packedTripNameFlow(
    tripRepository: TripRepository,
    garmentId: GarmentId,
    clock: Clock,
): Flow<String?> =
    tripRepository
        .observeTrips()
        .map { trips ->
            val today = LocalDate.now(clock)
            trips.firstOrNull { !today.isBefore(it.dateRange.start) && !today.isAfter(it.dateRange.end) }
        }.map { activeTrip ->
            activeTrip?.let { trip ->
                tripRepository
                    .observePackingList(trip.id)
                    .first()
                    .firstOrNull { it.garmentId == garmentId && it.isPacked }
                    ?.let { trip.name ?: trip.destination }
            }
        }

internal fun averageInterval(sortedDates: List<LocalDate>): Double? {
    if (sortedDates.size < 2) return null
    return sortedDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.average()
}

private const val DAYS_PER_MONTH = 30.0

internal fun wearFrequency(
    firstWorn: LocalDate?,
    totalWears: Int,
    today: LocalDate,
): Double {
    if (firstWorn == null || totalWears == 0) return 0.0
    val daysSinceFirst = ChronoUnit.DAYS.between(firstWorn, today).coerceAtLeast(1)
    return totalWears / (daysSinceFirst / DAYS_PER_MONTH)
}

private const val ROTATION_MIDPOINT = 50.0
private const val ROTATION_MAX = 100.0

/** `0..100`; `50` = right on this item's own historical rewear schedule,
 * `>50` = overdue, `<50` = worn recently relative to its own pattern.
 * `null` when there's not yet enough history (fewer than two wears) to
 * define "its own pattern" at all. */
internal fun rotationScoreFor(
    lastWorn: LocalDate?,
    averageDaysBetweenWears: Double?,
    today: LocalDate,
): Int? {
    if (lastWorn == null || averageDaysBetweenWears == null || averageDaysBetweenWears <= 0.0) return null
    val daysSinceLastWorn = ChronoUnit.DAYS.between(lastWorn, today).toDouble()
    val raw = (daysSinceLastWorn / averageDaysBetweenWears) * ROTATION_MIDPOINT
    return raw.coerceIn(0.0, ROTATION_MAX).roundToInt()
}

internal fun buildOutfitInsights(
    outfit: Outfit,
    wearEvents: List<WearEvent>,
    voteCounts: FeedbackVoteCountRow,
    allGarments: List<Garment>,
    occasions: List<Occasion>,
    clock: Clock,
): OutfitInsights {
    val ownWears =
        wearEvents
            .filter { it.outfitId == outfit.id && it.status == WearEventStatus.WORN }
            .sortedByDescending { it.date }
    val lastWorn = ownWears.firstOrNull()?.date
    val rating = voteCounts.totalVotes.takeIf { it > 0 }?.let { OutfitRating(voteCounts.positiveVotes, it) }
    val garmentsById = allGarments.associateBy { it.id }
    val memberGarments = outfit.garments.mapNotNull { slot -> garmentsById[slot.garmentId] }
    val averageDaysBetweenWears = averageInterval(ownWears.map { it.date }.sorted())
    val suitableOccasionIds =
        occasions
            .filter { occasion -> occasion.impliedDressCode()?.let { it in outfit.dressCodes } == true }
            .map { it.id }
            .toSet()
    return OutfitInsights(
        outfitId = outfit.id,
        lastWornDate = lastWorn,
        timesWorn = ownWears.size,
        averageRating = rating,
        isFavorite = outfit.isFavorite,
        suitableSeasons = outfit.seasons,
        suitableDressCodes = outfit.dressCodes,
        suitableOccasionIds = suitableOccasionIds,
        suitableWeather = suitableWeatherOf(memberGarments),
        estimatedComfort = comfortLevelOf(memberGarments),
        estimatedWarmth = warmthLevelOf(memberGarments),
        rotationPriority = rotationScoreFor(lastWorn, averageDaysBetweenWears, LocalDate.now(clock)),
    )
}

private const val WARMTH_LIGHT_MAX = 2.0
private const val WARMTH_WARM_MIN = 4.0

internal fun comfortLevelOf(garments: List<Garment>): ComfortLevel {
    val relaxedCount = garments.count { it.fit == Fit.RELAXED || it.fit == Fit.OVERSIZED }
    val structuredCount = garments.count { it.fit == Fit.SLIM }
    return when {
        relaxedCount > structuredCount -> ComfortLevel.RELAXED
        structuredCount > relaxedCount -> ComfortLevel.STRUCTURED
        else -> ComfortLevel.MODERATE
    }
}

internal fun warmthLevelOf(garments: List<Garment>): WarmthLevel {
    val averageWarmth = garments.mapNotNull { it.warmthRating }.map { it.toDouble() }.average()
    return when {
        averageWarmth.isNaN() -> WarmthLevel.MODERATE
        averageWarmth <= WARMTH_LIGHT_MAX -> WarmthLevel.LIGHT
        averageWarmth >= WARMTH_WARM_MIN -> WarmthLevel.WARM
        else -> WarmthLevel.MODERATE
    }
}

/** A simple, honestly-labeled heuristic from member garments' average
 * `warmthRating` — never a real forecast match (see `GarmentInsights`'s
 * "1..5 scale" note, the same one Phase 6's weather filter already uses). */
internal fun suitableWeatherOf(garments: List<Garment>): Set<WeatherCondition> {
    val averageWarmth = garments.mapNotNull { it.warmthRating }.map { it.toDouble() }.average()
    return when {
        averageWarmth.isNaN() -> setOf(WeatherCondition.SUNNY, WeatherCondition.CLOUDY)
        averageWarmth >= WARMTH_WARM_MIN -> setOf(WeatherCondition.SNOW, WeatherCondition.STORM, WeatherCondition.FOG)
        averageWarmth <= WARMTH_LIGHT_MAX -> setOf(WeatherCondition.SUNNY)
        else -> setOf(WeatherCondition.SUNNY, WeatherCondition.CLOUDY)
    }
}
