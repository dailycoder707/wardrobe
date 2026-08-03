package com.wardrobe.app.core.model.common

import java.time.LocalDate

/**
 * Used by [com.wardrobe.app.core.model.trip.Trip] (start/end) and by
 * [com.wardrobe.app.core.domain] stats queries that take a window (a second real use,
 * same justification as [Money]).
 */
data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "end ($end) must not be before start ($start)" }
    }
}
