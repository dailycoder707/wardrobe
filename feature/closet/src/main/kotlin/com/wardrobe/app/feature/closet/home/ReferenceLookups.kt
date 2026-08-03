package com.wardrobe.app.feature.closet.home

import com.wardrobe.app.core.model.common.BrandId
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.ColorId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.stats.DormantItem
import com.wardrobe.app.core.model.stats.UsageStats
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.feature.closet.common.toTileUiModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Bundles every id-keyed lookup [buildHomeInsights] needs so its own
 * signature stays short — see `phase-5e-wardrobe-intelligence.md`'s design
 * decisions for why Home Insights lives here rather than growing
 * `HomeViewModel` itself. */
internal data class ReferenceLookups(
    val garmentsById: Map<GarmentId, Garment>,
    val categoriesById: Map<CategoryId, Category>,
    val brandsById: Map<BrandId, Brand>,
    val colorsById: Map<ColorId, Color>,
    val outfitsById: Map<OutfitId, Outfit>,
)

internal fun buildHomeInsights(
    usageStats: UsageStats,
    lookups: ReferenceLookups,
    dormant: List<DormantItem>,
    upcomingEvents: List<WearEvent>,
    today: LocalDate,
): HomeInsightsUiModel =
    HomeInsightsUiModel(
        mostUsedGarment =
            usageStats.mostWornGarmentIds.firstNotNullOfOrNull {
                lookups.garmentsById[it]?.toTileUiModel(lookups.categoriesById, lookups.brandsById)
            },
        waitingToBeWornGarment =
            dormant
                .sortedWith(compareBy(nullsFirst()) { it.lastWornDate })
                .firstNotNullOfOrNull {
                    lookups.garmentsById[it.garmentId]?.toTileUiModel(lookups.categoriesById, lookups.brandsById)
                },
        recentlyPurchasedGarment =
            lookups.garmentsById.values
                .filter { it.purchaseDate != null }
                .maxByOrNull { it.purchaseDate ?: LocalDate.MIN }
                ?.toTileUiModel(lookups.categoriesById, lookups.brandsById),
        favoriteColorName = usageStats.signatureColorIds.firstNotNullOfOrNull { lookups.colorsById[it]?.name },
        favoriteBrandName = usageStats.favouriteBrandIds.firstNotNullOfOrNull { lookups.brandsById[it]?.name },
        favoriteCategoryName =
            usageStats.favouriteCategoryIds.firstNotNullOfOrNull { lookups.categoriesById[it]?.name },
        upcomingOutfitLabel = buildUpcomingOutfitLabel(upcomingEvents, lookups, today),
    )

private fun buildUpcomingOutfitLabel(
    upcomingEvents: List<WearEvent>,
    lookups: ReferenceLookups,
    today: LocalDate,
): String? {
    val next = upcomingEvents.filter { it.status == WearEventStatus.PLANNED }.minByOrNull { it.date } ?: return null
    val name =
        next.outfitId?.let { lookups.outfitsById[it]?.name?.takeUnless(String::isBlank) }
            ?: next.garmentId?.let { lookups.garmentsById[it]?.name }
            ?: "something"
    return "$name — ${relativeDayLabel(next.date, today)}"
}

private fun relativeDayLabel(
    date: LocalDate,
    today: LocalDate,
): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days <= 0 -> "today"
        days == 1L -> "tomorrow"
        else -> "in $days days"
    }
}
