package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.StyleRule
import com.wardrobe.app.core.model.styling.StyleRuleType
import com.wardrobe.app.core.model.weather.WeatherCondition
import com.wardrobe.app.core.model.weather.WeatherSnapshot

private const val WEATHER_MATCH_BONUS = 2.0
private const val LAYERING_WARMTH_THRESHOLD = 4
private const val PLANNED_OCCASION_BONUS = 2.0
private val RAINY_CONDITIONS = setOf(WeatherCondition.RAIN, WeatherCondition.STORM)

/** Whether [rule] rejects [garment] outright — `AVOID_CATEGORY`/`AVOID_BRAND`
 * are the only rule types this engine auto-enforces per garment; everything
 * else (`AVOID_COLOR_COMBO`/`ALWAYS_INCLUDE_CATEGORY`) is a whole-outfit
 * check `OutfitAssembler` runs post-assembly instead. */
internal fun ruleMatchesGarment(
    rule: StyleRule,
    garment: Garment,
): Boolean {
    val params = decodeRuleParameters(rule.parametersJson)
    return when (rule.ruleType) {
        StyleRuleType.AVOID_CATEGORY -> params["categoryId"]?.toLongOrNull() == garment.categoryId.value
        StyleRuleType.AVOID_BRAND -> params["brandId"]?.toLongOrNull() == garment.brandId?.value
        else -> false
    }
}

/** How many of [EngineInput.activeRules] actually rejected at least one
 * candidate garment — Developer Panel diagnostics only
 * (`RecommendationRunDiagnostics.rulesAppliedCount`), covers only the
 * per-garment `AVOID_CATEGORY`/`AVOID_BRAND` rules [ruleMatchesGarment]
 * enforces, not the whole-outfit rules `OutfitAssembler` checks post-
 * assembly — a stated simplification (phase-7-context-aware-assistant.md). */
internal fun countAppliedAvoidRules(input: EngineInput): Int =
    input.activeRules.count { rule -> input.candidateGarments.any { garment -> ruleMatchesGarment(rule, garment) } }

/**
 * Phase 7's weather-aware scoring (never a hard requirement — [EngineInput.weather]
 * is `null`-safe throughout, Constitution rule 12): rewards a warm outerwear
 * layer or warm shoes on a cold day, an outerwear layer or scarf on a rainy
 * day. Distinct from `passesWeatherFilter`, which *excludes* a garment
 * outright rather than nudging its score — this only ever adds, never
 * subtracts, so weather can make an outfit better without ever making one
 * impossible to assemble.
 */
internal fun weatherFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor {
    val weather = input.weather
    val category = input.categoriesById[garment.categoryId]
    if (weather == null || category == null) return ScoreFactor(0.0)
    val slot = OutfitSlot.classify(category.name)
    return coldWeatherFactor(garment, weather, slot)
        ?: rainyWeatherFactor(weather, slot, category)
        ?: ScoreFactor(0.0)
}

private fun coldWeatherFactor(
    garment: Garment,
    weather: WeatherSnapshot,
    slot: OutfitSlot?,
): ScoreFactor? {
    val apparentHigh = weather.apparentTempHighC ?: weather.currentTempC
    val warmth = garment.warmthRating ?: 0
    val isCold = apparentHigh != null && apparentHigh <= COLD_APPARENT_TEMP_C && warmth >= LAYERING_WARMTH_THRESHOLD
    return when {
        !isCold -> null
        slot == OutfitSlot.OUTERWEAR -> ScoreFactor(WEATHER_MATCH_BONUS, "it's cool out, so a warmer layer helps")
        slot == OutfitSlot.SHOES -> ScoreFactor(WEATHER_MATCH_BONUS, "these are better suited to cooler weather")
        else -> null
    }
}

private fun rainyWeatherFactor(
    weather: WeatherSnapshot,
    slot: OutfitSlot?,
    category: Category,
): ScoreFactor? {
    val isRainy = weather.condition in RAINY_CONDITIONS
    return when {
        !isRainy -> {
            null
        }

        slot == OutfitSlot.OUTERWEAR -> {
            ScoreFactor(WEATHER_MATCH_BONUS, "it may rain today, so a jacket helps")
        }

        AccessoryCategory.classify(category.name) == AccessoryCategory.SCARF -> {
            ScoreFactor(WEATHER_MATCH_BONUS, "it may rain today")
        }

        else -> {
            null
        }
    }
}

/** Biases toward whatever dress code today's planned calendar occasion
 * implies (`Occasion.impliedDressCode`, `core:model`) — separate from the
 * user's own general [com.wardrobe.app.core.model.styling.RecommendationPreferences.preferredDressCodes]
 * bias, since this is about *today specifically*, not the user's usual
 * taste. `null`-safe: no planned occasion (or one with no recognizable
 * dress code) means this factor simply never fires. */
internal fun plannedOccasionFactor(
    garment: Garment,
    input: EngineInput,
): ScoreFactor {
    val plannedDressCode = input.plannedOccasionDressCode ?: return ScoreFactor(0.0)
    return if (plannedDressCode in garment.dressCodes) {
        ScoreFactor(PLANNED_OCCASION_BONUS, "it matches your plans for today")
    } else {
        ScoreFactor(0.0)
    }
}
