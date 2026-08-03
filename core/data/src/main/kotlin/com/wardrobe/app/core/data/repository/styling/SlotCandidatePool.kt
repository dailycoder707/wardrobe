package com.wardrobe.app.core.data.repository.styling

import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationPreferences

/* Phase 9 — OutfitAssembler's accessory/jewelry candidate-pool and
 * smart-completion selection helpers, split into their own file purely to
 * stay under detekt's TooManyFunctions ceiling; OutfitAssembler.kt keeps
 * the whole-outfit assembly logic that actually calls these. */

/** Phase 9 — every candidate scored twice over: [SlotCandidatePool.weatherSafe]
 * (what [pickNthSmart] tries first) and [SlotCandidatePool.all] (the
 * smart-completion fallback, tried only when [SlotCandidatePool.weatherSafe]
 * has nothing left) — built once per slot/category so a fallback never
 * re-scores from scratch. */
internal data class SlotCandidatePool(
    val weatherSafe: List<ScoredCandidate>,
    val all: List<ScoredCandidate>,
)

internal fun buildPool(
    garments: List<Garment>,
    input: EngineInput,
): SlotCandidatePool {
    val scoredAll = garments.map { scoreCandidate(it, input) }.sortedByDescending { it.score }
    val scoredSafe = scoredAll.filter { passesWeatherFilter(it.garment, input.weather) }
    return SlotCandidatePool(scoredSafe, scoredAll)
}

/** Groups raw candidates for one [OutfitSlot] (`ACCESSORIES`/`JEWELRY`) into a
 * pool per finer sub-category ([AccessoryCategory]/[JewelryCategory]) — the
 * mechanism that lets the engine recommend a belt *and* a scarf, or a necklace
 * *and* a ring, concurrently, without ever touching `OutfitSlot`'s one-item-
 * per-slot persisted shape. */
internal fun <C> categorizedPool(
    rawCandidates: List<Garment>,
    input: EngineInput,
    classify: (String) -> C,
): Map<C, SlotCandidatePool> =
    rawCandidates
        .mapNotNull { garment -> input.categoriesById[garment.categoryId]?.let { classify(it.name) to garment } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, garments) -> buildPool(garments, input) }

private fun wantedAccessoryCategories(preferences: RecommendationPreferences): Set<AccessoryCategory> =
    setOfNotNull(
        AccessoryCategory.BELT.takeIf { preferences.includeBelt },
        AccessoryCategory.HAIR_ACCESSORY.takeIf { preferences.includeHairAccessories },
        AccessoryCategory.SUNGLASSES.takeIf { preferences.includeSunglasses },
        AccessoryCategory.SCARF.takeIf { preferences.includeScarf },
    )

/** One item per *wanted* [AccessoryCategory] the wardrobe has something
 * available for — smart-completion (via [pickNthSmart]) applies per category
 * independently, so a scarf missing today's weather-safe pool doesn't block a
 * belt that has one. */
internal fun pickAccessories(
    input: EngineInput,
    pools: Map<AccessoryCategory, SlotCandidatePool>,
    variation: Int,
): List<Pair<AccessoryCategory, ScoredCandidate>> =
    wantedAccessoryCategories(input.preferences).mapNotNull { category ->
        pickNthSmart(pools[category], variation)?.let { category to it }
    }

/** The jewelry equivalent of [pickAccessories] — [JewelryCategory.OTHER] is
 * excluded since it's the "couldn't classify" catch-all, not a real category
 * to recommend one-of. */
internal fun pickJewelry(
    input: EngineInput,
    pools: Map<JewelryCategory, SlotCandidatePool>,
    variation: Int,
): List<Pair<JewelryCategory, ScoredCandidate>> {
    if (!input.preferences.includeJewelry) return emptyList()
    return JewelryCategory.entries.filter { it != JewelryCategory.OTHER }.mapNotNull { category ->
        pickNthSmart(pools[category], variation)?.let { category to it }
    }
}

internal fun pickNth(
    candidates: List<ScoredCandidate>?,
    n: Int,
): ScoredCandidate? = candidates?.getOrNull(n.coerceAtMost((candidates.size - 1).coerceAtLeast(0)))

private const val SMART_COMPLETION_REASON =
    "it doesn't perfectly match today's weather, but nothing else is available in this category right now"

/** Phase 9's "smart outfit completion": tries [SlotCandidatePool.weatherSafe]
 * first, falling back to [SlotCandidatePool.all] (tagging the pick with an
 * explicit reason) only when the weather-safe subset has nothing left for
 * this [n] — a slot is left empty only when the wardrobe genuinely has
 * nothing in that category, never merely because the weather filter zeroed
 * out the safe subset. */
internal fun pickNthSmart(
    pool: SlotCandidatePool?,
    n: Int,
): ScoredCandidate? {
    val safePick = pool?.let { pickNth(it.weatherSafe, n) }
    val fallbackPick = pool?.let { pickNth(it.all, n) }?.let { it.copy(reasons = it.reasons + SMART_COMPLETION_REASON) }
    return safePick ?: fallbackPick
}
