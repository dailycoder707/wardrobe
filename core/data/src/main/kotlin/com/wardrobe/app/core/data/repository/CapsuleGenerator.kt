package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.intelligence.CapsuleSuggestion
import com.wardrobe.app.core.model.intelligence.CapsuleType
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.stats.CostPerWearEntry

private data class CapsulePreset(
    val seasons: Set<Season>,
    val dressCodes: Set<DressCode>,
    val perSlotCount: Map<OutfitSlot, Int>,
    val explanation: String,
)

private const val OFFICE_TOPS = 3
private const val OFFICE_BOTTOMS = 2
private const val OFFICE_SHOES = 2
private const val TRAVEL_TOPS = 3
private const val TRAVEL_BOTTOMS = 2
private const val WEEKEND_TOPS = 3
private const val WEEKEND_BOTTOMS = 2
private const val CASUAL_TOPS = 4
private const val CASUAL_BOTTOMS = 3
private const val CASUAL_SHOES = 2
private const val MINIMAL_TOPS = 2
private const val MINIMAL_BOTTOMS = 2
private const val RAINY_BOTTOMS = 2
private const val COLD_OUTERWEAR = 2
private const val COLD_TOPS = 3
private const val COLD_BOTTOMS = 2
private const val SUMMER_TOPS = 3
private const val SUMMER_BOTTOMS = 2

// A named val per capsule type, not a `when` expression, so `presetFor` stays a
// one-line lookup — keeps this file well under detekt's LongMethod/TooManyFunctions
// ceilings instead of trading one violation for the other.
private val OFFICE_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = setOf(DressCode.BUSINESS, DressCode.SMART_CASUAL),
        perSlotCount =
            mapOf(
                OutfitSlot.TOP to OFFICE_TOPS,
                OutfitSlot.BOTTOM to OFFICE_BOTTOMS,
                OutfitSlot.SHOES to OFFICE_SHOES,
                OutfitSlot.BAG to 1,
            ),
        explanation = "A small set of business-appropriate pieces that mix and match for the work week.",
    )

private val TRAVEL_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = setOf(DressCode.CASUAL, DressCode.SMART_CASUAL),
        perSlotCount =
            mapOf(
                OutfitSlot.TOP to TRAVEL_TOPS,
                OutfitSlot.BOTTOM to TRAVEL_BOTTOMS,
                OutfitSlot.SHOES to 1,
                OutfitSlot.OUTERWEAR to 1,
            ),
        explanation = "Versatile, easy-to-pack pieces that combine into several looks.",
    )

private val WEEKEND_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = setOf(DressCode.CASUAL),
        perSlotCount =
            mapOf(OutfitSlot.TOP to WEEKEND_TOPS, OutfitSlot.BOTTOM to WEEKEND_BOTTOMS, OutfitSlot.SHOES to 1),
        explanation = "Relaxed pieces for time off.",
    )

private val WEDDING_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = setOf(DressCode.FORMAL),
        perSlotCount =
            mapOf(OutfitSlot.DRESS to 1, OutfitSlot.SHOES to 1, OutfitSlot.JEWELRY to 1, OutfitSlot.BAG to 1),
        explanation = "Formal pieces suited to a wedding guest.",
    )

private val CASUAL_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = setOf(DressCode.CASUAL),
        perSlotCount =
            mapOf(OutfitSlot.TOP to CASUAL_TOPS, OutfitSlot.BOTTOM to CASUAL_BOTTOMS, OutfitSlot.SHOES to CASUAL_SHOES),
        explanation = "Everyday casual staples.",
    )

private val MINIMAL_PACKING_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = emptySet(),
        perSlotCount =
            mapOf(OutfitSlot.TOP to MINIMAL_TOPS, OutfitSlot.BOTTOM to MINIMAL_BOTTOMS, OutfitSlot.SHOES to 1),
        explanation = "The smallest set of pieces that still combine into several outfits.",
    )

private val RAINY_WEATHER_PRESET =
    CapsulePreset(
        seasons = emptySet(),
        dressCodes = emptySet(),
        perSlotCount = mapOf(OutfitSlot.OUTERWEAR to 1, OutfitSlot.SHOES to 1, OutfitSlot.BOTTOM to RAINY_BOTTOMS),
        explanation = "Pieces suited to wet weather.",
    )

private val COLD_WEATHER_PRESET =
    CapsulePreset(
        seasons = setOf(Season.WINTER),
        dressCodes = emptySet(),
        perSlotCount =
            mapOf(
                OutfitSlot.OUTERWEAR to COLD_OUTERWEAR,
                OutfitSlot.TOP to COLD_TOPS,
                OutfitSlot.BOTTOM to COLD_BOTTOMS,
            ),
        explanation = "Warmer pieces for cold weather.",
    )

private val HOT_SUMMER_PRESET =
    CapsulePreset(
        seasons = setOf(Season.SUMMER),
        dressCodes = emptySet(),
        perSlotCount = mapOf(OutfitSlot.TOP to SUMMER_TOPS, OutfitSlot.BOTTOM to SUMMER_BOTTOMS, OutfitSlot.SHOES to 1),
        explanation = "Lighter, breathable pieces for hot weather.",
    )

private val PRESETS_BY_TYPE: Map<CapsuleType, CapsulePreset> =
    mapOf(
        CapsuleType.OFFICE to OFFICE_PRESET,
        CapsuleType.TRAVEL to TRAVEL_PRESET,
        CapsuleType.WEEKEND to WEEKEND_PRESET,
        CapsuleType.WEDDING to WEDDING_PRESET,
        CapsuleType.CASUAL to CASUAL_PRESET,
        CapsuleType.MINIMAL_PACKING to MINIMAL_PACKING_PRESET,
        CapsuleType.RAINY_WEATHER to RAINY_WEATHER_PRESET,
        CapsuleType.COLD_WEATHER to COLD_WEATHER_PRESET,
        CapsuleType.HOT_SUMMER to HOT_SUMMER_PRESET,
    )

private fun presetFor(type: CapsuleType): CapsulePreset = PRESETS_BY_TYPE.getValue(type)

private const val RARE_WEAR_THRESHOLD = 3
private const val FAVORITE_BONUS = 2.0
private const val RARE_WEAR_BONUS = 1.0
private const val MATCH_BONUS = 1.0

private fun capsuleScore(
    garment: Garment,
    preset: CapsulePreset,
    costPerWearByGarmentId: Map<GarmentId, CostPerWearEntry>,
): Double {
    val wearCount = costPerWearByGarmentId[garment.id]?.totalWearCount ?: 0
    var score = 0.0
    if (garment.isFavorite) score += FAVORITE_BONUS
    if (wearCount <= RARE_WEAR_THRESHOLD) score += RARE_WEAR_BONUS
    if (preset.seasons.isNotEmpty() && garment.seasons.any { it in preset.seasons }) score += MATCH_BONUS
    if (preset.dressCodes.isNotEmpty() && garment.dressCodes.any { it in preset.dressCodes }) score += MATCH_BONUS
    return score
}

/**
 * A small, curated per-[CapsuleType] item set (Phase 9), scored by a
 * lightweight rule (favorite + rarely-worn + season/dress-code match
 * bonuses) over the user's *own* existing wardrobe. Deliberately simpler
 * than the full Phase 6 `RecommendationRuleEngine` (whose scoring internals
 * are private to `core.data.repository.styling` and built around a full
 * outfit assembly, not a curated set) — a capsule is interchangeable pieces,
 * not one scored outfit, so its own small scoring rule is enough. Still
 * fully rule-based/explainable/offline, and never invents an item the
 * wardrobe doesn't actually have — [CapsuleSuggestion.itemsBySlot] simply
 * omits a slot entirely when nothing qualifies.
 */
internal fun generateCapsule(
    type: CapsuleType,
    garments: List<Garment>,
    categoriesById: Map<CategoryId, Category>,
    costPerWearByGarmentId: Map<GarmentId, CostPerWearEntry>,
): CapsuleSuggestion {
    val preset = presetFor(type)
    val bySlot = garments.groupBy { garment -> categoriesById[garment.categoryId]?.name?.let(OutfitSlot::classify) }
    val itemsBySlot =
        preset.perSlotCount
            .mapValues { (slot, count) ->
                bySlot[slot]
                    .orEmpty()
                    .filter { preset.seasons.isEmpty() || it.seasons.any { season -> season in preset.seasons } }
                    .filter { preset.dressCodes.isEmpty() || it.dressCodes.any { code -> code in preset.dressCodes } }
                    .sortedByDescending { capsuleScore(it, preset, costPerWearByGarmentId) }
                    .take(count)
                    .map { it.id }
            }.filterValues { it.isNotEmpty() }
    return CapsuleSuggestion(type, itemsBySlot, preset.explanation)
}
