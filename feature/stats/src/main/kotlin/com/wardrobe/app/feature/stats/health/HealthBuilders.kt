package com.wardrobe.app.feature.stats.health

import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.feature.stats.common.recordTiming
import com.wardrobe.app.feature.stats.common.toInsightItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val DOMINANT_COLOR_SHARE_THRESHOLD = 0.5
private const val NEGLECT_THRESHOLD_DAYS = 60L
private const val UNUSED_PURCHASE_WINDOW_DAYS = 180L
private const val RECENTLY_ADDED_WINDOW_DAYS = 30L
private const val OVERUSE_MULTIPLIER = 2.0
private const val MAX_ITEMS_PER_ADVISORY = 6

internal fun buildHealthAdvisories(
    ref: HealthReferenceData,
    stats: HealthStatsData,
    today: LocalDate,
): List<HealthAdvisoryUiModel> =
    recordTiming("wardrobeHealth") {
        listOfNotNull(
            colorBalanceAdvisory(ref),
            reliableFavoritesAdvisory(ref, stats),
            neglectedItemsAdvisory(ref, stats, today),
            favoriteBrandsAdvisory(stats),
            unusedPurchasesAdvisory(ref, stats, today),
            recentlyRefreshedAdvisory(ref, stats, today),
            categoriesWithNothingYetAdvisory(ref),
        )
    }

private fun colorBalanceAdvisory(ref: HealthReferenceData): HealthAdvisoryUiModel? {
    val withColor = ref.garments.mapNotNull { it.primaryColorId }
    val dominant = withColor.groupingBy { it }.eachCount().maxByOrNull { it.value }
    return dominant?.let { (dominantColorId, count) ->
        ref.colorsById[dominantColorId]?.name?.let { colorName ->
            val share = count.toDouble() / withColor.size
            val message =
                if (share >= DOMINANT_COLOR_SHARE_THRESHOLD) {
                    "A good chunk of your closet leans toward $colorName. Nothing wrong with a signature color!"
                } else {
                    "Your color palette feels nicely balanced."
                }
            HealthAdvisoryUiModel("Color Balance", message)
        }
    }
}

private fun reliableFavoritesAdvisory(
    ref: HealthReferenceData,
    stats: HealthStatsData,
): HealthAdvisoryUiModel? {
    val everWorn = stats.costPerWear.filter { it.totalWearCount > 0 }
    val median =
        everWorn
            .map { it.totalWearCount }
            .sorted()
            .getOrNull(everWorn.size / 2)
            ?.toDouble() ?: 0.0
    val garmentsById = ref.garments.associateBy { it.id }
    val favorites =
        everWorn
            .filter { median > 0 && it.totalWearCount >= median * OVERUSE_MULTIPLIER }
            .sortedByDescending { it.totalWearCount }
            .take(MAX_ITEMS_PER_ADVISORY)
            .mapNotNull { entry -> garmentsById[entry.garmentId]?.toInsightItem("Worn ${entry.totalWearCount} times") }
    return favorites.takeIf { it.isNotEmpty() }?.let {
        HealthAdvisoryUiModel(
            title = "Reliable Favorites",
            message = "You reach for these again and again — real wardrobe workhorses.",
            items = it,
        )
    }
}

private fun neglectedItemsAdvisory(
    ref: HealthReferenceData,
    stats: HealthStatsData,
    today: LocalDate,
): HealthAdvisoryUiModel? {
    val garmentsById = ref.garments.associateBy { it.id }
    val neglected =
        stats.dormant
            .filter { item ->
                item.lastWornDate == null ||
                    ChronoUnit.DAYS.between(item.lastWornDate, today) >= NEGLECT_THRESHOLD_DAYS
            }.take(MAX_ITEMS_PER_ADVISORY)
            .mapNotNull { item -> garmentsById[item.garmentId]?.toInsightItem("Hasn't had a turn in a while") }
    if (neglected.isEmpty()) return null
    return HealthAdvisoryUiModel(
        title = "Ready for a Turn",
        message = "A little variety could go a long way — these could use some rotation.",
        items = neglected,
    )
}

private fun favoriteBrandsAdvisory(stats: HealthStatsData): HealthAdvisoryUiModel? {
    if (stats.usage.favouriteBrandIds.isEmpty()) return null
    return HealthAdvisoryUiModel(
        title = "Favorite Brands",
        message = "You keep coming back to a few brands you trust — that's a good sign of knowing your own style.",
    )
}

private fun unusedPurchasesAdvisory(
    ref: HealthReferenceData,
    stats: HealthStatsData,
    today: LocalDate,
): HealthAdvisoryUiModel? {
    val neverWornIds =
        stats.costPerWear
            .filter { it.totalWearCount == 0 }
            .map { it.garmentId }
            .toSet()
    val unused =
        ref.garments
            .mapNotNull { garment ->
                val purchaseDate = garment.purchaseDate ?: return@mapNotNull null
                val isRecentPurchase = ChronoUnit.DAYS.between(purchaseDate, today) <= UNUSED_PURCHASE_WINDOW_DAYS
                garment.takeIf { it.id in neverWornIds && isRecentPurchase }?.toInsightItem("Not worn yet")
            }.take(MAX_ITEMS_PER_ADVISORY)
    if (unused.isEmpty()) return null
    return HealthAdvisoryUiModel(
        title = "Recent Purchases to Try",
        message = "A few recent additions haven't made it into rotation yet — maybe give one a try this week.",
        items = unused,
    )
}

private fun recentlyRefreshedAdvisory(
    ref: HealthReferenceData,
    stats: HealthStatsData,
    today: LocalDate,
): HealthAdvisoryUiModel? {
    val wornAtLeastOnceIds =
        stats.costPerWear
            .filter { it.totalWearCount > 0 }
            .map { it.garmentId }
            .toSet()
    val refreshed =
        ref.garments
            .filter { it.id in wornAtLeastOnceIds && it.isRecentlyAdded(today) }
            .take(MAX_ITEMS_PER_ADVISORY)
            .map { it.toInsightItem("Already in rotation") }
    if (refreshed.isEmpty()) return null
    return HealthAdvisoryUiModel(
        title = "Off to a Great Start",
        message = "You've already started wearing your newest pieces.",
        items = refreshed,
    )
}

private fun Garment.isRecentlyAdded(today: LocalDate): Boolean {
    val addedDate = createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(addedDate, today) <= RECENTLY_ADDED_WINDOW_DAYS
}

private fun categoriesWithNothingYetAdvisory(ref: HealthReferenceData): HealthAdvisoryUiModel? {
    val usedCategoryIds = ref.garments.map { it.categoryId }.toSet()
    val emptyTopCategories =
        ref.categories
            .filter { it.level == CategoryLevel.TOP && it.id !in usedCategoryIds }
            .map { it.name }
    return emptyTopCategories.takeIf { ref.garments.isNotEmpty() && it.isNotEmpty() }?.let { categories ->
        HealthAdvisoryUiModel(
            title = "Unexplored Categories",
            message =
                "You don't have anything in ${categories.joinToString(", ")} yet — " +
                    "maybe that's next, or maybe you just don't need it.",
        )
    }
}
