package com.wardrobe.app.core.data.repository

import com.wardrobe.app.core.database.dao.CategoryGarmentCountRow
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.intelligence.DuplicateGroup
import com.wardrobe.app.core.model.intelligence.ShoppingGapSuggestion
import com.wardrobe.app.core.model.intelligence.WardrobeHealthScore
import com.wardrobe.app.core.model.stats.UsageStats
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Phase 9 — [WardrobeIntelligenceRepositoryImpl]'s shopping-gap,
 * duplicate-group, and wardrobe-health-score builders, split out for the
 * same `TooManyFunctions`-avoidance reason as the other `*Builders.kt`
 * sibling files in this package. */

private const val GAP_ABSOLUTE_MAX = 3
private const val GAP_MEDIAN_DIVISOR = 3.0

internal fun buildShoppingGaps(
    countRows: List<CategoryGarmentCountRow>,
    namesById: Map<Long, Category>,
): List<ShoppingGapSuggestion> {
    val counts = countRows.filter { it.garmentCount > 0 }
    val mostOwned = counts.maxByOrNull { it.garmentCount }
    val mostOwnedName = mostOwned?.let { namesById[it.categoryId]?.name }
    return if (counts.size < 2 || mostOwned == null || mostOwnedName == null) {
        emptyList()
    } else {
        val median = counts.map { it.garmentCount }.sorted()[counts.size / 2]
        counts
            .filter {
                it.categoryId != mostOwned.categoryId &&
                    it.garmentCount <= GAP_ABSOLUTE_MAX &&
                    it.garmentCount < median / GAP_MEDIAN_DIVISOR
            }.mapNotNull { row -> shoppingGapFor(row, namesById, mostOwned, mostOwnedName) }
    }
}

private fun shoppingGapFor(
    row: CategoryGarmentCountRow,
    namesById: Map<Long, Category>,
    mostOwned: CategoryGarmentCountRow,
    mostOwnedName: String,
): ShoppingGapSuggestion? {
    val name = namesById[row.categoryId]?.name ?: return null
    return ShoppingGapSuggestion(
        categoryId = CategoryId(row.categoryId),
        categoryName = name,
        ownedCount = row.garmentCount,
        comparisonCategoryName = mostOwnedName,
        comparisonCount = mostOwned.garmentCount,
        message = "You own ${mostOwned.garmentCount} $mostOwnedName but only ${row.garmentCount} $name.",
    )
}

private const val SIMILAR_USAGE_TOLERANCE = 0.25

internal fun buildDuplicateGroups(
    garments: List<Garment>,
    costRows: List<com.wardrobe.app.core.database.dao.CostPerWearRow>,
): List<DuplicateGroup> {
    val wearCountById = costRows.associate { GarmentId(it.garmentId) to it.wearCount }
    return garments
        .filter { it.primaryColorId != null }
        .groupBy { it.categoryId to it.primaryColorId }
        .values
        .filter { it.size >= 2 }
        .map { group -> duplicateGroupFor(group, wearCountById) }
}

private fun duplicateGroupFor(
    group: List<Garment>,
    wearCountById: Map<GarmentId, Int>,
): DuplicateGroup {
    val brandIds = group.mapNotNull { it.brandId }.toSet()
    val wearCounts = group.map { wearCountById[it.id] ?: 0 }
    val maxWear = wearCounts.maxOrNull() ?: 0
    val minWear = wearCounts.minOrNull() ?: 0
    val similarUsage = maxWear == 0 || (maxWear - minWear) <= maxWear * SIMILAR_USAGE_TOLERANCE
    return DuplicateGroup(
        garmentIds = group.map { it.id },
        matchedOnCategory = true,
        matchedOnColor = true,
        matchedOnBrand = brandIds.size == 1,
        similarUsage = similarUsage,
    )
}

private const val ROTATION_MAX = 100.0
private const val USAGE_WEIGHT = 0.4
private const val ROTATION_WEIGHT = 0.3
private const val FRESHNESS_WEIGHT = 0.3

internal fun buildWardrobeHealthScore(
    usage: UsageStats,
    wearCounts: List<Int>,
    forgottenCount: Int,
): WardrobeHealthScore {
    val rotationBalance = rotationBalanceScore(wearCounts)
    val forgottenRatio =
        if (usage.totalActiveGarments > 0) forgottenCount.toDouble() / usage.totalActiveGarments else 0.0
    val freshness = (1.0 - forgottenRatio) * ROTATION_MAX
    val score =
        (USAGE_WEIGHT * usage.usagePercent + ROTATION_WEIGHT * rotationBalance + FRESHNESS_WEIGHT * freshness)
            .roundToInt()
            .coerceIn(0, ROTATION_MAX.toInt())
    return WardrobeHealthScore(
        score = score,
        rotationScore = rotationBalance.roundToInt().coerceIn(0, ROTATION_MAX.toInt()),
        usagePercent = usage.usagePercent.roundToInt(),
        forgottenCount = forgottenCount,
    )
}

/** `100` when the wardrobe wears evenly (low coefficient of variation across
 * wear counts), trending toward `0` as a handful of items dominate — an
 * explicitly labeled composite heuristic, not a scientific balance metric. */
private fun rotationBalanceScore(wearCounts: List<Int>): Double {
    val mean = wearCounts.average()
    return if (wearCounts.size < 2 || mean <= 0.0) {
        ROTATION_MAX
    } else {
        val variance = wearCounts.sumOf { (it - mean) * (it - mean) } / wearCounts.size
        val coefficientOfVariation = sqrt(variance) / mean
        (ROTATION_MAX - coefficientOfVariation * ROTATION_MAX).coerceIn(0.0, ROTATION_MAX)
    }
}
