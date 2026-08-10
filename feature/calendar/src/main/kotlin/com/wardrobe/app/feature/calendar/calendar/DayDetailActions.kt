package com.wardrobe.app.feature.calendar.calendar

import com.wardrobe.app.core.model.common.OccasionId

/** Groups Day Detail's write callbacks so [DayDetailPanel] and [CalendarBody] stay under
 * detekt's LongParameterList threshold without hiding any of the distinct actions
 * behind a vaguer, harder-to-read single lambda. */
data class DayDetailActions(
    val onLogWear: () -> Unit,
    val onClearDay: () -> Unit,
    val onDuplicateDay: () -> Unit,
    val onScheduleRecurring: (Long) -> Unit,
    val onRescheduleEvent: (Long) -> Unit,
    val onConfirmWorn: (Long) -> Unit,
    val onDeleteEvent: (Long) -> Unit,
    /** M20 Part 6 — starts a fresh recommendation request for the selected date. */
    val onGetRecommendation: () -> Unit,
    /** M20 Part 11 — starts a recommendation request that will replace the
     * given already-planned event's outfit instead of adding a new one. */
    val onReplaceEvent: (Long) -> Unit,
    /** M20 Part 7 — assigns (or clears, via `null`) the occasion for the
     * selected date. */
    val onOccasionSelected: (OccasionId?) -> Unit,
)
