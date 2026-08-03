package com.wardrobe.app.core.model.weather

import com.wardrobe.app.core.model.garment.Season
import java.time.LocalDate
import java.time.Month

/**
 * A meteorological, Northern-hemisphere month-bucket mapping (Mar-May =
 * SPRING, Jun-Aug = SUMMER, Sep-Nov = AUTUMN, Dec-Feb = WINTER) — used by
 * Phase 9's per-garment "Season Usage" stat and Trip Intelligence's seasonal
 * packing bias, since neither has a reliable hemisphere/location signal to
 * do better with (`WeatherLocationResolver` only resolves *current* device
 * location, not a future trip's destination). Southern-hemisphere users get
 * an honestly-wrong-but-labeled mapping here, not a silently wrong one — the
 * same "this season = last 90 days" disclosed simplification Phase 5e's
 * Wardrobe Story already carries (`TECHNICAL_DEBT.md` item 10).
 */
fun LocalDate.toMeteorologicalSeason(): Season =
    when (month) {
        Month.MARCH, Month.APRIL, Month.MAY -> Season.SPRING
        Month.JUNE, Month.JULY, Month.AUGUST -> Season.SUMMER
        Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> Season.AUTUMN
        else -> Season.WINTER
    }
