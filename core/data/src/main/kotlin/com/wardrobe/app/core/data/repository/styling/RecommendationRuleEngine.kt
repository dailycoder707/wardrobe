package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import java.time.temporal.ChronoUnit

private const val BASE_SCORE = 1.0
private const val FAVORITE_BONUS = 3.0
private const val RECENTLY_WORN_PENALTY = 5.0
private const val RARE_WEAR_BONUS = 1.5
private const val RARE_WEAR_MAX_COUNT = 2
private const val COLOR_BIAS_BONUS = 1.0
private const val DRESS_CODE_BONUS = 2.0
private const val COMFORT_BONUS = 0.5
internal const val COLD_APPARENT_TEMP_C = 10.0
private const val HOT_APPARENT_TEMP_C = 27.0
private const val MIN_WARMTH_FOR_COLD = 3
private const val MAX_WARMTH_FOR_HOT = 3

internal data class ScoredCandidate(
    val garment: Garment,
    val score: Double,
    val reasons: List<String>,
)

/**
 * Buckets every rule-eligible candidate garment by its inferred [OutfitSlot] —
 * excludes anything already filtered out of [EngineInput.candidateGarments]
 * (laundry, non-active status, away on a trip — see
 * [com.wardrobe.app.core.data.repository.StylingEngineRepositoryImpl]'s loader)
 * plus anything an active `AVOID_CATEGORY`/`AVOID_BRAND` rule rejects
 * outright (see `ContextScoring.kt`'s `ruleMatchesGarment`).
 */
internal fun buildSlotCandidates(input: EngineInput): Map<OutfitSlot, List<Garment>> =
    input.candidateGarments
        .filterNot { garment -> violatesAvoidRule(garment, input) }
        .mapNotNull { garment ->
            val category = input.categoriesById[garment.categoryId] ?: return@mapNotNull null
            val slot = OutfitSlot.classify(category.name) ?: return@mapNotNull null
            slot to garment
        }.groupBy({ it.first }, { it.second })

private fun violatesAvoidRule(
    garment: Garment,
    input: EngineInput,
): Boolean = input.activeRules.any { rule -> ruleMatchesGarment(rule, garment) }

internal data class ScoreFactor(
    val delta: Double,
    val reason: String? = null,
)

/** Scores one candidate for one slot by summing a handful of small,
 * independent factors — each contributing factor appends a plain-language
 * [ScoredCandidate.reasons] entry so the explanation engine never has to
 * reverse-engineer *why* a score landed where it did. Split into one
 * function per factor (rather than one long `if`-chain) specifically to keep
 * this function's own cyclomatic complexity low regardless of how many
 * preferences the scoring model grows to consider. [weatherFactor]/
 * [plannedOccasionFactor] (Phase 7) live in the sibling `ContextScoring.kt`
 * file, alongside the rule-matching helpers, to keep this file's own
 * function count bounded. */
internal fun scoreCandidate(
    garment: Garment,
    input: EngineInput,
): ScoredCandidate {
    val factors =
        listOf(
            favoriteFactor(garment, input),
            rotationFactor(garment, input),
            rareWearFactor(garment, input),
            colorBiasFactor(garment, input),
            dressCodeFactor(garment, input),
            comfortFactor(garment, input),
            weatherFactor(garment, input),
            plannedOccasionFactor(garment, input),
        )
    val score = BASE_SCORE + factors.sumOf { it.delta }
    return ScoredCandidate(garment, score, factors.mapNotNull { it.reason })
}

private fun favoriteFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor =
    if (garment.isFavorite && input.preferences.preferFavorites) {
        ScoreFactor(FAVORITE_BONUS, "it's one of your favorites")
    } else {
        ScoreFactor(0.0)
    }

/** Penalizes a garment worn within the repeat interval, or — when it hasn't
 * been worn recently at all — surfaces that as a positive reason instead. */
private fun rotationFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor {
    val lastWornDate = input.costPerWearByGarmentId[garment.id]?.lastWornDate ?: return ScoreFactor(0.0)
    val daysSinceWorn = ChronoUnit.DAYS.between(lastWornDate, input.today)
    return when {
        input.preferences.avoidRecentlyWorn && daysSinceWorn < input.preferences.maxRepeatIntervalDays -> {
            ScoreFactor(-RECENTLY_WORN_PENALTY)
        }

        daysSinceWorn >= input.preferences.maxRepeatIntervalDays -> {
            ScoreFactor(0.0, "you haven't worn it for $daysSinceWorn days")
        }

        else -> {
            ScoreFactor(0.0)
        }
    }
}

private fun rareWearFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor {
    val totalWearCount = input.costPerWearByGarmentId[garment.id]?.totalWearCount ?: 0
    return if (input.preferences.preferRarelyWorn && totalWearCount <= RARE_WEAR_MAX_COUNT) {
        ScoreFactor(RARE_WEAR_BONUS, "it doesn't get much wear")
    } else {
        ScoreFactor(0.0)
    }
}

private fun colorBiasFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor =
    if (input.preferences.favoriteColorBias && garment.primaryColorId in input.favoriteColorIds) {
        ScoreFactor(COLOR_BIAS_BONUS, "it's one of your favorite colors")
    } else {
        ScoreFactor(0.0)
    }

private fun dressCodeFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor =
    if (input.preferences.preferredDressCodes.isNotEmpty() &&
        garment.dressCodes.any { it in input.preferences.preferredDressCodes }
    ) {
        ScoreFactor(DRESS_CODE_BONUS, "it fits your usual style")
    } else {
        ScoreFactor(0.0)
    }

private fun comfortFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor =
    if (input.preferences.preferComfortableFit && garment.fit in COMFORTABLE_FITS) {
        ScoreFactor(COMFORT_BONUS)
    } else {
        ScoreFactor(0.0)
    }

private val COMFORTABLE_FITS = setOf(Fit.RELAXED, Fit.OVERSIZED)

/**
 * The Constitution's hard weather filter (phase-1-architecture.md, "Quality bar
 * for the styling engine") — scores against *apparent* temperature, never raw.
 * Thresholds are a simple, defensible starting point, not tuned against real
 * forecast data at scale — see phase-7-context-aware-assistant.md's Known
 * Limitations.
 */
internal fun passesWeatherFilter(
    garment: Garment,
    weather: WeatherSnapshot?,
): Boolean {
    val warmth = garment.warmthRating
    val apparentHigh = weather?.apparentTempHighC
    if (warmth == null || apparentHigh == null) return true
    val tooLightForCold = apparentHigh <= COLD_APPARENT_TEMP_C && warmth < MIN_WARMTH_FOR_COLD
    val tooHeavyForHot = apparentHigh >= HOT_APPARENT_TEMP_C && warmth > MAX_WARMTH_FOR_HOT
    return !tooLightForCold && !tooHeavyForHot
}
