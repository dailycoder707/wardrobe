package com.wardrobe.app.feature.calendar.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.common.WearEventId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentFilter
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.intelligence.CalendarConflict
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitFilter
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.feature.calendar.common.toTileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5000L
private const val EPOCH_YEAR = 2020
private val HISTORY_START_DATE: LocalDate = LocalDate.of(EPOCH_YEAR, 1, 1)
private const val FUTURE_WINDOW_YEARS = 1L
private const val RECURRING_WEEK_COUNT = 8
private const val DAYS_PER_WEEK = 7
private const val CONFLICT_LOOKAHEAD_DAYS = 14

private data class ReferenceContext(
    val garments: List<Garment>,
    val outfits: List<Outfit>,
    val occasions: List<Occasion>,
)

private data class UiInputs(
    val viewMode: CalendarViewMode,
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val toast: String?,
)

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        wearEventRepository: WearEventRepository,
        outfitRepository: OutfitRepository,
        garmentRepository: GarmentRepository,
        occasionRepository: OccasionRepository,
        wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
    ) : ViewModel() {
        private val viewModeState = MutableStateFlow(CalendarViewMode.CALENDAR)
        private val visibleMonthState = MutableStateFlow(YearMonth.now())
        private val selectedDateState = MutableStateFlow(LocalDate.now())
        private val toastMessage = MutableStateFlow<String?>(null)

        private val today: LocalDate = LocalDate.now()
        private val queryRange = DateRange(HISTORY_START_DATE, today.plusYears(FUTURE_WINDOW_YEARS))

        /** Every write path (logging/rescheduling/clearing/duplicating wear events) lives on
         * [CalendarEventActions] rather than as `CalendarViewModel` methods directly — keeping
         * them there is what keeps this class under detekt's TooManyFunctions threshold without
         * hiding any behavior behind an artificial merge of unrelated actions. */
        val actions =
            CalendarEventActions(
                wearEventRepository = wearEventRepository,
                scope = viewModelScope,
                queryRange = queryRange,
                today = today,
                toastMessage = toastMessage,
            )

        private val referenceFlow =
            combine(
                garmentRepository.observeGarments(GarmentFilter(status = GarmentStatus.ACTIVE)),
                outfitRepository.observeOutfits(OutfitFilter(isSaved = null, isArchived = null)),
                occasionRepository.observeAll(),
            ) { garments, outfits, occasions -> ReferenceContext(garments, outfits, occasions) }

        private val uiInputsFlow =
            combine(
                viewModeState,
                visibleMonthState,
                selectedDateState,
                toastMessage,
            ) { mode, month, selected, toast ->
                UiInputs(mode, month, selected, toast)
            }

        private val conflictsFlow = wardrobeIntelligenceRepository.observeCalendarConflicts(CONFLICT_LOOKAHEAD_DAYS)

        val uiState: StateFlow<CalendarUiState> =
            combine(
                wearEventRepository.observeEvents(queryRange),
                referenceFlow,
                uiInputsFlow,
                conflictsFlow,
            ) { events, reference, inputs, conflicts ->
                buildCalendarUiState(events, reference, inputs, today, conflicts)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CalendarUiState(isLoading = true),
            )

        fun onMonthChange(monthDelta: Long) {
            visibleMonthState.update { it.plusMonths(monthDelta) }
        }

        fun onSelectDate(date: LocalDate) {
            selectedDateState.value = date
            visibleMonthState.value = YearMonth.from(date)
        }

        fun onToggleViewMode() {
            viewModeState.update {
                if (it == CalendarViewMode.CALENDAR) CalendarViewMode.LIST else CalendarViewMode.CALENDAR
            }
        }

        fun onToastShown() {
            toastMessage.value = null
        }
    }

/** Holds every wear-event write path (logging, rescheduling, clearing, duplicating, recurring
 * scheduling) separately from [CalendarViewModel] itself so the ViewModel's own read-model
 * plumbing doesn't get crowded out — see the `actions` property's KDoc for why this split
 * exists. */
class CalendarEventActions(
    private val wearEventRepository: WearEventRepository,
    private val scope: CoroutineScope,
    private val queryRange: DateRange,
    private val today: LocalDate,
    private val toastMessage: MutableStateFlow<String?>,
) {
    fun onLogGarmentWear(
        garmentId: GarmentId,
        date: LocalDate,
    ) {
        logWear(garmentId = garmentId, outfitId = null, date = date)
    }

    fun onLogOutfitWear(
        outfitId: OutfitId,
        date: LocalDate,
    ) {
        logWear(garmentId = null, outfitId = outfitId, date = date)
    }

    private fun logWear(
        garmentId: GarmentId?,
        outfitId: OutfitId?,
        date: LocalDate,
    ) {
        scope.launch {
            val status = if (date.isAfter(today)) WearEventStatus.PLANNED else WearEventStatus.WORN
            wearEventRepository.logWear(
                WearEvent(
                    id = WearEventId(0),
                    date = date,
                    garmentId = garmentId,
                    outfitId = outfitId,
                    weatherCacheId = null,
                    occasionId = null,
                    note = null,
                    status = status,
                    createdAt = Instant.now(),
                ),
            )
            toastMessage.value = if (status == WearEventStatus.PLANNED) "Scheduled for $date" else "Logged for $date"
        }
    }

    /** "Recurring outfit" (Phase 5d scope decision, see `phase-5d-wardrobe-stylist.md`):
     * materializes real `PLANNED` rows for the next [RECURRING_WEEK_COUNT] weeks
     * rather than a persisted recurrence rule engine — simpler, fully visible/
     * editable in the calendar, and avoids inventing RRULE-style infrastructure
     * this app has no other use for yet. */
    fun onScheduleRecurringOutfit(
        outfitId: OutfitId,
        startDate: LocalDate,
    ) {
        scope.launch {
            repeat(RECURRING_WEEK_COUNT) { weekIndex ->
                val date = startDate.plusWeeks(weekIndex.toLong())
                wearEventRepository.logWear(
                    WearEvent(
                        id = WearEventId(0),
                        date = date,
                        garmentId = null,
                        outfitId = outfitId,
                        weatherCacheId = null,
                        occasionId = null,
                        note = null,
                        status = if (date.isAfter(today)) WearEventStatus.PLANNED else WearEventStatus.WORN,
                        createdAt = Instant.now(),
                    ),
                )
            }
            toastMessage.value = "Scheduled every week for $RECURRING_WEEK_COUNT weeks"
        }
    }

    fun onRescheduleEvent(
        eventId: Long,
        newDate: LocalDate,
    ) {
        scope.launch {
            val event = findEvent(eventId) ?: return@launch
            val newStatus = if (newDate.isAfter(today)) WearEventStatus.PLANNED else event.status
            wearEventRepository.updateWear(event.copy(date = newDate, status = newStatus))
            toastMessage.value = "Moved to $newDate"
        }
    }

    fun onConfirmWorn(eventId: Long) {
        scope.launch {
            val event = findEvent(eventId) ?: return@launch
            wearEventRepository.updateWear(event.copy(status = WearEventStatus.WORN))
        }
    }

    fun onDeleteEvent(eventId: Long) {
        scope.launch { wearEventRepository.deleteEvent(WearEventId(eventId)) }
    }

    fun onClearDay(date: LocalDate) {
        scope.launch {
            wearEventRepository.clearDay(date)
            toastMessage.value = "Cleared $date"
        }
    }

    fun onDuplicateDay(
        from: LocalDate,
        to: LocalDate,
    ) {
        scope.launch {
            wearEventRepository.duplicateDay(from, to)
            toastMessage.value = "Duplicated to $to"
        }
    }

    private suspend fun findEvent(eventId: Long): WearEvent? =
        wearEventRepository.observeEvents(queryRange).first().firstOrNull { it.id.value == eventId }
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}

private fun buildCalendarUiState(
    events: List<WearEvent>,
    reference: ReferenceContext,
    inputs: UiInputs,
    today: LocalDate,
    conflicts: List<CalendarConflict>,
): CalendarUiState {
    val garmentsById = reference.garments.associateBy { it.id }
    val outfitsById = reference.outfits.associateBy { it.id }
    val eventsByDate = events.groupBy { it.date }
    val conflictDates = conflicts.map { it.date }.toSet()

    val monthDays = buildMonthDays(inputs.visibleMonth, eventsByDate, today, conflictDates)
    val selectedDayEvents =
        eventsByDate[inputs.selectedDate]
            .orEmpty()
            .map { it.toUiModel(garmentsById, outfitsById, reference.occasions) }
    val selectedDayConflictMessages = conflicts.filter { it.date == inputs.selectedDate }.map { it.message }

    val historyByMonth =
        if (inputs.viewMode == CalendarViewMode.LIST) {
            events
                .filter { it.status == WearEventStatus.WORN }
                .sortedByDescending { it.date }
                .groupBy { YearMonth.from(it.date) }
                .map { (month, monthEvents) ->
                    val entries = monthEvents.map { it.toUiModel(garmentsById, outfitsById, reference.occasions) }
                    MonthHistoryGroup(month, entries)
                }
        } else {
            emptyList()
        }

    return CalendarUiState(
        isLoading = false,
        viewMode = inputs.viewMode,
        visibleMonth = inputs.visibleMonth,
        monthDays = monthDays,
        selectedDate = inputs.selectedDate,
        selectedDayEvents = selectedDayEvents,
        historyByMonth = historyByMonth,
        savedOutfits =
            reference.outfits.filter { it.isSaved && !it.isArchived }.map { outfit ->
                SimpleOutfitOption(
                    id = outfit.id.value,
                    name = outfit.name?.takeUnless { it.isBlank() } ?: "Untitled look",
                    thumbnailPath =
                        outfit.garments.sortedBy { it.layerSlot }.firstNotNullOfOrNull { slot ->
                            garmentsById[slot.garmentId]
                                ?.images
                                ?.firstOrNull { it.type == ImageType.THUMBNAIL }
                                ?.filePath
                        },
                )
            },
        closetGarments = reference.garments.map { it.toTileUiModel() },
        toastMessage = inputs.toast,
        selectedDayConflictMessages = selectedDayConflictMessages,
    )
}

private fun buildMonthDays(
    month: YearMonth,
    eventsByDate: Map<LocalDate, List<WearEvent>>,
    today: LocalDate,
    conflictDates: Set<LocalDate>,
): List<DayCellUiModel> {
    val firstOfMonth = month.atDay(1)
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leadingBlankDays = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val fullWeeksLength = leadingBlankDays + month.lengthOfMonth() + (DAYS_PER_WEEK - 1)
    val totalCells = (fullWeeksLength / DAYS_PER_WEEK) * DAYS_PER_WEEK

    return (0 until totalCells).map { offset ->
        val date = gridStart(firstOfMonth, leadingBlankDays).plusDays(offset.toLong())
        val dayEvents = eventsByDate[date].orEmpty()
        DayCellUiModel(
            date = date,
            isCurrentMonth = YearMonth.from(date) == month,
            isToday = date == today,
            wornCount = dayEvents.count { it.status == WearEventStatus.WORN },
            plannedCount = dayEvents.count { it.status == WearEventStatus.PLANNED },
            hasConflict = date in conflictDates,
        )
    }
}

private fun gridStart(
    firstOfMonth: LocalDate,
    leadingBlankDays: Int,
): LocalDate = firstOfMonth.minusDays(leadingBlankDays.toLong())

private fun WearEvent.toUiModel(
    garmentsById: Map<GarmentId, Garment>,
    outfitsById: Map<OutfitId, Outfit>,
    occasions: List<Occasion>,
): WearEventUiModel {
    val garment = garmentId?.let { garmentsById[it] }
    val outfit = outfitId?.let { outfitsById[it] }
    val outfitThumbnail =
        outfit?.garments?.sortedBy { it.layerSlot }?.firstNotNullOfOrNull { slot ->
            garmentsById[slot.garmentId]?.images?.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
        }
    return WearEventUiModel(
        id = id.value,
        date = date,
        title =
            garment?.name?.takeUnless { it.isBlank() }
                ?: outfit?.name?.takeUnless { it.isBlank() }
                ?: if (outfit != null) "Untitled look" else "Untitled item",
        thumbnailPath = garment?.images?.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath ?: outfitThumbnail,
        isOutfit = outfitId != null,
        sourceId = outfitId?.value ?: garmentId?.value ?: 0L,
        status = status,
        occasionName = occasionId?.let { occ -> occasions.firstOrNull { it.id == occ }?.name },
    )
}
