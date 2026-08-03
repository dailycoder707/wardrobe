package com.wardrobe.app.feature.stats.story

import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.feature.stats.common.recordTiming
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency

private const val MIN_DORMANT_DAYS_TO_MENTION = 30L
private const val SEASON_WINDOW_DAYS = 90L
private const val MIN_REWEARS_TO_COUNT_SAVINGS = 2

internal fun buildStoryCards(
    ref: StoryReferenceData,
    stats: StoryStatsData,
    weekdayDressCode: DressCode?,
    today: LocalDate,
): List<StoryCardUiModel> =
    recordTiming("wardrobeStory") {
        listOfNotNull(
            outfitsWornCard(stats.outfitsWornCount),
            favoriteColorCard(stats, ref),
            longestUnwornCard(stats, ref, today),
            outfitsCreatedCard(ref, today),
            costSavedCard(stats, ref),
            weekendStyleCard(stats.weekendDressCode, weekdayDressCode),
        )
    }

private fun outfitsWornCard(count: Int): StoryCardUiModel? {
    if (count <= 0) return null
    val noun = if (count == 1) "outfit" else "outfits"
    return StoryCardUiModel("You've worn $count $noun this year.")
}

private fun favoriteColorCard(
    stats: StoryStatsData,
    ref: StoryReferenceData,
): StoryCardUiModel? {
    val colorName =
        stats.usage.signatureColorIds
            .firstOrNull()
            ?.let { ref.colorsById[it]?.name } ?: return null
    return StoryCardUiModel("Your most-loved color this year is $colorName.")
}

private fun longestUnwornCard(
    stats: StoryStatsData,
    ref: StoryReferenceData,
    today: LocalDate,
): StoryCardUiModel? {
    val garmentsById = ref.garments.associateBy { it.id }
    val candidate =
        stats.dormant
            .sortedWith(compareBy(nullsFirst()) { it.lastWornDate })
            .firstNotNullOfOrNull { item ->
                garmentsById[item.garmentId]?.let { garment -> garment to item.lastWornDate }
            }
    return candidate?.let { (garment, lastWornDate) -> longestUnwornHeadline(garment, lastWornDate, today) }
}

private fun longestUnwornHeadline(
    garment: Garment,
    lastWornDate: LocalDate?,
    today: LocalDate,
): StoryCardUiModel? {
    // Deliberately derived from [today] (itself built from the injected `Clock`
    // in StoryViewModel), never `Instant.now()` — the same clock-injection
    // discipline phase-5a-data-layer.md established, here to keep this
    // function's "days since" math testable against a fixed clock.
    val addedDate = garment.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
    val daysSinceAdded = ChronoUnit.DAYS.between(addedDate, today)
    val daysSinceWorn = lastWornDate?.let { ChronoUnit.DAYS.between(it, today) }
    val qualifyingDays = daysSinceWorn ?: daysSinceAdded
    if (qualifyingDays < MIN_DORMANT_DAYS_TO_MENTION) return null

    val title = garment.name?.takeUnless { it.isBlank() } ?: "piece"
    val headline =
        if (lastWornDate == null) {
            "You haven't worn your $title yet — maybe today's the day."
        } else {
            "You haven't worn your $title in $daysSinceWorn days."
        }
    return StoryCardUiModel(headline, StoryNavigationTarget.Garment(garment.id.value))
}

private fun outfitsCreatedCard(
    ref: StoryReferenceData,
    today: LocalDate,
): StoryCardUiModel? {
    val seasonStart = today.minusDays(SEASON_WINDOW_DAYS)
    val count =
        ref.outfits.count { outfit ->
            val createdDate = outfit.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
            !createdDate.isBefore(seasonStart)
        }
    if (count <= 0) return null
    return StoryCardUiModel("You've created $count outfits in the last few months.")
}

/** Only shown when every rewear-worthy garment (worn more than once, priced)
 * shares one currency — mixing currencies into one number would be
 * misleading, not just imprecise, so this card is skipped rather than shown
 * with a number that doesn't mean anything (see `phase-5e-wardrobe-
 * intelligence.md`'s Known Limitations). */
private fun costSavedCard(
    stats: StoryStatsData,
    ref: StoryReferenceData,
): StoryCardUiModel? {
    val garmentsById = ref.garments.associateBy { it.id }
    val pricedRewears =
        stats.costPerWear
            .filter { it.totalWearCount >= MIN_REWEARS_TO_COUNT_SAVINGS && it.costPerWear != null }
            .mapNotNull { entry ->
                val garment = garmentsById[entry.garmentId] ?: return@mapNotNull null
                val price = garment.price ?: return@mapNotNull null
                Triple(price.currencyCode, price.amount, entry.costPerWear ?: return@mapNotNull null)
            }
    val currencies = pricedRewears.map { it.first }.distinct()
    val saved = pricedRewears.sumOf { (_, price, costPerWear) -> price - costPerWear }

    return if (pricedRewears.isEmpty() || currencies.size != 1 || saved <= 0) {
        null
    } else {
        StoryCardUiModel(
            "You've saved ${formatSavings(saved, currencies.single())} by rewearing pieces you already own " +
                "instead of buying something new.",
        )
    }
}

private fun formatSavings(
    amount: Double,
    currencyCode: String,
): String {
    val format =
        runCatching {
            NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode) }
        }.getOrNull()
    return format?.format(amount) ?: NumberFormat.getNumberInstance().format(amount)
}

private fun weekendStyleCard(
    weekendDressCode: DressCode?,
    weekdayDressCode: DressCode?,
): StoryCardUiModel? {
    if (weekendDressCode == null || weekdayDressCode == null || weekendDressCode == weekdayDressCode) {
        return null
    }
    val weekendLabel = weekendDressCode.naturalLabel()
    val weekdayLabel = weekdayDressCode.naturalLabel()
    return StoryCardUiModel("You tend to go $weekendLabel on weekends and $weekdayLabel on weekdays.")
}

private fun DressCode.naturalLabel(): String =
    when (this) {
        DressCode.CASUAL -> "casual"
        DressCode.SMART_CASUAL -> "smart-casual"
        DressCode.BUSINESS -> "business"
        DressCode.FORMAL -> "formal"
        DressCode.ATHLETIC -> "athletic"
        DressCode.LOUNGE -> "lounge"
    }
