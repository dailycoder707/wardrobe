package com.wardrobe.app.feature.closet.closet

import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.Season
import java.time.LocalDate
import java.time.Month

/** A single, real, derived wardrobe stat surfaced above the grid — never an
 * AI label ("AI picked", "Perfect match"), always a plain count computed
 * from the actual active-garment list (M17 Part 7I). Tapping one applies
 * [filterToApply], the same way any other filter chip does. */
data class ClosetInsight(
    val label: String,
    val count: Int,
    val filterToApply: ClosetFilterState,
)

/** Meteorological Northern-Hemisphere season by month — a documented
 * simplification, not a claim about the user's actual location (the app has
 * no hemisphere/location signal for wardrobe purposes; `WeatherRepository`'s
 * forecast lookup is a separate, opt-in concern). Consistent with this
 * codebase's existing "good enough, honestly stated" heuristics (e.g.
 * [com.wardrobe.app.core.model.outfit.impliedDressCode]). */
fun currentSeason(date: LocalDate): Season =
    when (date.month) {
        Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> Season.WINTER
        Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
        Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
        else -> Season.AUTUMN
    }

/** Real, derived-from-actual-data wardrobe insights — favorites, current-
 * season items, and "work-ready" items (via [ClosetFilterPreset.WORK]'s
 * dress codes). Computed over every active garment regardless of the
 * current search/filter, so it always describes the whole wardrobe. Any
 * insight with a zero count is omitted rather than shown as "0 items." */
fun computeClosetInsights(
    garments: List<Garment>,
    today: LocalDate,
): List<ClosetInsight> {
    val season = currentSeason(today)
    val favoritesCount = garments.count { it.isFavorite }
    val seasonCount = garments.count { season in it.seasons }
    val workDressCodes = ClosetFilterPreset.WORK.dressCodes
    val workReadyCount = garments.count { garment -> garment.dressCodes.any { it in workDressCodes } }

    return listOfNotNull(
        insightOrNull(favoritesCount, "favorite") { ClosetFilterState.EMPTY.copy(favoriteOnly = true) },
        insightOrNull(seasonCount, "${season.displayLabel().lowercase()} item") {
            ClosetFilterState.EMPTY.copy(seasons = setOf(season))
        },
        insightOrNull(workReadyCount, "work-ready item") {
            ClosetFilterState.EMPTY.copy(dressCodes = ClosetFilterPreset.WORK.dressCodes)
        },
    )
}

private fun insightOrNull(
    count: Int,
    noun: String,
    filterToApply: () -> ClosetFilterState,
): ClosetInsight? {
    if (count == 0) return null
    val plural = if (count == 1) noun else "${noun}s"
    return ClosetInsight("$count $plural", count, filterToApply())
}
