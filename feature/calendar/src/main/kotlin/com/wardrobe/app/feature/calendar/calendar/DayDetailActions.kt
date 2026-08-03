package com.wardrobe.app.feature.calendar.calendar

/** Groups Day Detail's write callbacks so [DayDetailPanel] and [CalendarBody] stay under
 * detekt's LongParameterList threshold without hiding any of the seven distinct actions
 * behind a vaguer, harder-to-read single lambda. */
data class DayDetailActions(
    val onLogWear: () -> Unit,
    val onClearDay: () -> Unit,
    val onDuplicateDay: () -> Unit,
    val onScheduleRecurring: (Long) -> Unit,
    val onRescheduleEvent: (Long) -> Unit,
    val onConfirmWorn: (Long) -> Unit,
    val onDeleteEvent: (Long) -> Unit,
)
