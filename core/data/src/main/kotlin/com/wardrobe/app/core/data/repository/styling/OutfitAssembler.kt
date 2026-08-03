package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.domain.styling.ColorHarmony
import com.wardrobe.app.core.domain.styling.analyzeColorHarmony
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitGarmentSlot
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.AccessoryItemExplanation
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.JewelryItemExplanation
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.StyleRuleType
import java.time.Instant

/** Optional slots included only when [EngineInput.preferences] says so *and* the
 * wardrobe actually has something available for them — never a forced-empty
 * slot, per the Wardrobe Completeness Rule's "explicitly explains why a
 * category is omitted" half. [OutfitSlot.ACCESSORIES]/[OutfitSlot.JEWELRY] are
 * deliberately not in this list (Phase 9) — they're resolved separately via
 * [pickAccessories]/[pickJewelry], one item per applicable sub-category rather
 * than a single best-of-slot pick. */
private fun optionalSlotsToTry(input: EngineInput): List<Pair<OutfitSlot, Boolean>> =
    listOf(
        OutfitSlot.OUTERWEAR to true,
        OutfitSlot.SHOES to input.preferences.includeShoes,
        OutfitSlot.BAG to input.preferences.includeBags,
        OutfitSlot.WATCH to input.preferences.includeWatch,
    )

/**
 * Builds up to [count] complete, scored outfits. Each successive outfit in the
 * list uses the next-best candidate per slot (rather than repeating the same
 * top pick) so "Generate Another Look" produces genuine variety, not the
 * identical outfit re-scored — see phase-6-personal-wardrobe-stylist.md's
 * recommendation strategy.
 */
internal fun generateRecommendations(
    input: EngineInput,
    count: Int,
    anchorGarment: Garment? = null,
): List<ScoredOutfit> {
    val slotCandidates = buildSlotCandidates(input)
    val scoredBySlot = slotCandidates.mapValues { (_, garments) -> buildPool(garments, input) }
    val accessoryPools =
        categorizedPool(slotCandidates[OutfitSlot.ACCESSORIES].orEmpty(), input, AccessoryCategory::classify)
    val jewelryPools = categorizedPool(slotCandidates[OutfitSlot.JEWELRY].orEmpty(), input, JewelryCategory::classify)

    return (0 until count).mapNotNull { variation ->
        assembleOneOutfit(input, scoredBySlot, accessoryPools, jewelryPools, variation, anchorGarment)
    }
}

/** Delegates to [buildPicks] (which returns `null` for an incomplete
 * variation) and, when complete, [buildScoredOutfit] — split this way, plus
 * [placeAnchor] below, specifically to keep every function's own return-
 * statement count low, per detekt's `ReturnCount` rule, rather than one long
 * function with an early-return per failure case. */
private fun assembleOneOutfit(
    input: EngineInput,
    scoredBySlot: Map<OutfitSlot, SlotCandidatePool>,
    accessoryPools: Map<AccessoryCategory, SlotCandidatePool>,
    jewelryPools: Map<JewelryCategory, SlotCandidatePool>,
    variation: Int,
    anchorGarment: Garment?,
): ScoredOutfit? {
    val basePicks = buildPicks(input, scoredBySlot, variation, anchorGarment) ?: return null
    val accessoryPicks = pickAccessories(input, accessoryPools, variation)
    val jewelryPicks = pickJewelry(input, jewelryPools, variation)
    val picks = basePicks.toMutableMap()
    accessoryPicks.maxByOrNull { it.second.score }?.let { picks[OutfitSlot.ACCESSORIES] = it.second }
    jewelryPicks.maxByOrNull { it.second.score }?.let { picks[OutfitSlot.JEWELRY] = it.second }
    return picks
        .takeIf { passesWholeOutfitRules(it.values.map { candidate -> candidate.garment }, input) }
        ?.let { buildScoredOutfit(it, input, accessoryPicks, jewelryPicks) }
}

private fun buildPicks(
    input: EngineInput,
    scoredBySlot: Map<OutfitSlot, SlotCandidatePool>,
    variation: Int,
    anchorGarment: Garment?,
): Map<OutfitSlot, ScoredCandidate>? {
    val picks = mutableMapOf<OutfitSlot, ScoredCandidate>()
    val complete = placeAnchor(picks, input, anchorGarment) && pickTorsoSlots(picks, scoredBySlot, variation)
    if (!complete) return null
    for ((slot, wanted) in optionalSlotsToTry(input)) {
        if (!wanted || slot in picks) continue
        pickNthSmart(scoredBySlot[slot], variation)?.let { picks[slot] = it }
    }
    return picks
}

/** `true` when there's nothing to anchor on, or the anchor was placed
 * successfully; `false` only when an anchor was requested but its category
 * can't be classified into a slot. */
private fun placeAnchor(
    picks: MutableMap<OutfitSlot, ScoredCandidate>,
    input: EngineInput,
    anchorGarment: Garment?,
): Boolean {
    if (anchorGarment == null) return true
    val slot = input.categoriesById[anchorGarment.categoryId]?.let { OutfitSlot.classify(it.name) }
    slot?.let { picks[it] = ScoredCandidate(anchorGarment, BASE_ANCHOR_SCORE, listOf("you asked to build around it")) }
    return slot != null
}

private fun buildScoredOutfit(
    picks: Map<OutfitSlot, ScoredCandidate>,
    input: EngineInput,
    accessoryPicks: List<Pair<AccessoryCategory, ScoredCandidate>>,
    jewelryPicks: List<Pair<JewelryCategory, ScoredCandidate>>,
): ScoredOutfit {
    val outfitGarments = picks.map { (slot, candidate) -> OutfitGarmentSlot(candidate.garment.id, slot.slotIndex) }
    val harmony = colorHarmonyOf(picks.values.map { it.garment }, input.colorsById)
    val colorBonus = if (harmony != null && harmony != ColorHarmony.MIXED) COLOR_HARMONY_BONUS else 0.0
    val totalScore = picks.values.sumOf { it.score } + colorBonus
    val explanation = buildExplanation(picks, harmony)
    val outfit =
        Outfit(
            id = OutfitId(0),
            name = null,
            garments = outfitGarments,
            occasionId = null,
            source = OutfitSource.AI_SUGGESTED,
            isSaved = false,
            photoUri = null,
            createdAt = Instant.now(),
        )
    val accessoryItems =
        accessoryPicks.map { (category, candidate) ->
            AccessoryItemExplanation(category, candidate.garment.id, explainAccessoryItem(candidate, category))
        }
    val jewelryItems =
        jewelryPicks.map { (category, candidate) ->
            JewelryItemExplanation(category, candidate.garment.id, explainJewelryItem(candidate, category))
        }
    return ScoredOutfit(outfit, totalScore, explanation, passedWeatherFilter = true, accessoryItems, jewelryItems)
}

private const val BASE_ANCHOR_SCORE = 10.0

/** A dress *or* a top+bottom pair — mirrors Outfit Builder's own mutual-
 * exclusion rule (Phase 5d). Returns `false` when neither combination has
 * anything available, meaning this variation can't produce a complete outfit. */
private fun pickTorsoSlots(
    picks: MutableMap<OutfitSlot, ScoredCandidate>,
    scoredBySlot: Map<OutfitSlot, SlotCandidatePool>,
    variation: Int,
): Boolean {
    if (OutfitSlot.DRESS in picks || OutfitSlot.TOP in picks) return true

    val dress = pickNthSmart(scoredBySlot[OutfitSlot.DRESS], variation)
    val top = pickNthSmart(scoredBySlot[OutfitSlot.TOP], variation)
    val bottom = pickNthSmart(scoredBySlot[OutfitSlot.BOTTOM], variation)

    return when {
        top != null && bottom != null -> {
            picks[OutfitSlot.TOP] = top
            picks[OutfitSlot.BOTTOM] = bottom
            true
        }

        dress != null -> {
            picks[OutfitSlot.DRESS] = dress
            true
        }

        else -> {
            false
        }
    }
}

/** Whole-outfit rule checks — [StyleRuleType.AVOID_COLOR_COMBO] and
 * [StyleRuleType.ALWAYS_INCLUDE_CATEGORY] only make sense evaluated against the
 * assembled set, unlike the per-garment [StyleRuleType.AVOID_CATEGORY]/
 * [StyleRuleType.AVOID_BRAND] rules `buildSlotCandidates` already filtered on.
 * [StyleRuleType.CUSTOM] has no fixed parameter schema to auto-enforce — it's
 * shown to the user, not evaluated here (see this file's KDoc). */
private fun passesWholeOutfitRules(
    garments: List<Garment>,
    input: EngineInput,
): Boolean {
    val colorIds = garments.mapNotNull { it.primaryColorId?.value }.toSet()
    val categoryIds = garments.map { it.categoryId.value }.toSet()

    return input.activeRules.all { rule ->
        val params = decodeRuleParameters(rule.parametersJson)
        when (rule.ruleType) {
            StyleRuleType.AVOID_COLOR_COMBO -> {
                val a = params["colorIdA"]?.toLongOrNull()
                val b = params["colorIdB"]?.toLongOrNull()
                !(a != null && b != null && a in colorIds && b in colorIds)
            }

            StyleRuleType.ALWAYS_INCLUDE_CATEGORY -> {
                val required = params["categoryId"]?.toLongOrNull()
                required == null || required in categoryIds
            }

            else -> {
                true
            }
        }
    }
}

private fun colorHarmonyOf(
    garments: List<Garment>,
    colorsById: Map<ColorId, Color>,
): ColorHarmony? {
    val hexValues = garments.mapNotNull { it.primaryColorId?.let(colorsById::get)?.hexValue }
    return analyzeColorHarmony(hexValues)
}

private const val COLOR_HARMONY_BONUS = 1.0
