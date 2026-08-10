package com.wardrobe.app.feature.calendar.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wardrobe.app.core.domain.repository.GarmentRepository
import com.wardrobe.app.core.domain.repository.OccasionRepository
import com.wardrobe.app.core.domain.repository.OutfitRepository
import com.wardrobe.app.core.domain.repository.StatsRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.domain.repository.StylingEngineRepository.Companion.DEFAULT_SUGGESTION_COUNT
import com.wardrobe.app.core.domain.repository.WardrobeIntelligenceRepository
import com.wardrobe.app.core.domain.repository.WearEventRepository
import com.wardrobe.app.core.domain.repository.WeatherRepository
import com.wardrobe.app.core.model.common.DateRange
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OccasionId
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
import com.wardrobe.app.core.model.stats.CostPerWearEntry
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import com.wardrobe.app.core.model.wear.WearEvent
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.model.weather.TemperatureUnit
import com.wardrobe.app.core.model.weather.WeatherSnapshot
import com.wardrobe.app.feature.calendar.common.toTileUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
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
private const val RECOMMENDATION_COUNT_STEP = 3
private const val MAX_RECOMMENDATION_COUNT = 9
private const val RECOMMENDATION_FAILED_MESSAGE = "Couldn't generate a recommendation. Try again."
private const val CALENDAR_LOAD_FAILED_MESSAGE = "Couldn't load your calendar. Try again."

/** Not `private`: [CalendarRecommendationLiveState.referenceState] (a public
 * class, constructed from [CalendarScreen.kt]) holds one of these — Kotlin
 * requires the type to be at least as visible as the property exposing it. */
internal data class ReferenceContext(
    val garments: List<Garment>,
    val outfits: List<Outfit>,
    val occasions: List<Occasion>,
)

private data class UiInputs(
    val viewMode: CalendarViewMode,
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val toast: String?,
    val pendingOccasionId: OccasionId?,
)

private data class ExtraContext(
    val weather: DateWeatherUiState,
    val costPerWear: List<CostPerWearEntry>,
)

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        private val wearEventRepository: WearEventRepository,
        outfitRepository: OutfitRepository,
        garmentRepository: GarmentRepository,
        occasionRepository: OccasionRepository,
        wardrobeIntelligenceRepository: WardrobeIntelligenceRepository,
        stylingEngineRepository: StylingEngineRepository,
        private val weatherRepository: WeatherRepository,
        statsRepository: StatsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val viewModeState = MutableStateFlow(CalendarViewMode.CALENDAR)
        private val visibleMonthState = MutableStateFlow(YearMonth.now(clock))
        private val selectedDateState = MutableStateFlow(LocalDate.now(clock))
        private val toastMessage = MutableStateFlow<String?>(null)
        private val pendingOccasionState = MutableStateFlow<OccasionId?>(null)
        private val selectedDateWeatherState = MutableStateFlow<DateWeatherUiState>(DateWeatherUiState.Unavailable)
        private val selectedDateWeatherSnapshotState = MutableStateFlow<WeatherSnapshot?>(null)
        private val referenceState = MutableStateFlow(ReferenceContext(emptyList(), emptyList(), emptyList()))

        private val today: LocalDate = LocalDate.now(clock)
        private val queryRange = DateRange(HISTORY_START_DATE, today.plusYears(FUTURE_WINDOW_YEARS))

        private val mutableRecommendationState =
            MutableStateFlow<DayRecommendationUiState>(DayRecommendationUiState.Idle)
        val recommendationState: StateFlow<DayRecommendationUiState> = mutableRecommendationState.asStateFlow()

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
                pendingOccasionState = pendingOccasionState,
            )

        /** M20's "recommend/plan/replace for this date" write paths — same
         * "own class, not more `CalendarViewModel` methods" reasoning as
         * [actions] above. Reuses the exact M19 [StylingEngineRepository]
         * path — no second recommendation engine. */
        val recommendationActions =
            CalendarRecommendationActions(
                dependencies =
                    CalendarRecommendationDependencies(stylingEngineRepository, outfitRepository, wearEventRepository),
                liveState =
                    CalendarRecommendationLiveState(
                        selectedDateState = selectedDateState,
                        pendingOccasionState = pendingOccasionState,
                        weatherSnapshotState = selectedDateWeatherSnapshotState,
                        referenceState = referenceState,
                    ),
                scope = viewModelScope,
                clock = clock,
                today = today,
                queryRange = queryRange,
                state = mutableRecommendationState,
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
                pendingOccasionState,
            ) { mode, month, selected, toast, pendingOccasion ->
                UiInputs(mode, month, selected, toast, pendingOccasion)
            }

        private val conflictsFlow = wardrobeIntelligenceRepository.observeCalendarConflicts(CONFLICT_LOOKAHEAD_DAYS)

        private val extraContextFlow =
            combine(selectedDateWeatherState, statsRepository.observeCostPerWear()) { weather, costPerWear ->
                ExtraContext(weather, costPerWear)
            }

        val uiState: StateFlow<CalendarUiState> =
            combine(
                wearEventRepository.observeEvents(queryRange),
                referenceFlow,
                uiInputsFlow,
                conflictsFlow,
                extraContextFlow,
            ) { events, reference, inputs, conflicts, extra ->
                buildCalendarUiState(events, reference, inputs, today, conflicts, extra)
            }
                // M22 fix: this chain previously had no error boundary at all —
                // a throwing repository flow left the screen stuck loading
                // forever instead of surfacing an honest, retryable error.
                .catch { emit(CalendarUiState(isLoading = false, error = CALENDAR_LOAD_FAILED_MESSAGE)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = CalendarUiState(isLoading = true),
                )

        init {
            viewModelScope.launch { referenceFlow.collect { referenceState.value = it } }
            // `collectLatest`, not `collect`: selecting a new date before the
            // previous date's forecast resolved must cancel that in-flight
            // fetch, or a slow lookup for a date the user already navigated
            // away from could land after a faster one and show weather for
            // the wrong day (M20 bug hunt, Part 18).
            viewModelScope.launch {
                selectedDateState.collectLatest { date ->
                    val snapshot = weatherRepository.getForecastForConfiguredLocation(date)
                    selectedDateWeatherSnapshotState.value = snapshot
                    selectedDateWeatherState.value = resolveDateWeather(snapshot, date, today, TemperatureUnit.CELSIUS)
                }
            }
        }

        fun onMonthChange(monthDelta: Long) {
            visibleMonthState.update { it.plusMonths(monthDelta) }
        }

        fun onSelectDate(date: LocalDate) {
            selectedDateState.value = date
            visibleMonthState.value = YearMonth.from(date)
            pendingOccasionState.value = null
            mutableRecommendationState.value = DayRecommendationUiState.Idle
        }

        fun onToggleViewMode() {
            viewModeState.update {
                if (it == CalendarViewMode.CALENDAR) CalendarViewMode.LIST else CalendarViewMode.CALENDAR
            }
        }

        fun onToastShown() {
            toastMessage.value = null
        }

        /** Part 7 — one occasion per day, stored on that day's own
         * [WearEvent]s (never a second, parallel "calendar entry" table).
         * Persists immediately onto every event already logged for
         * [selectedDateState]'s date; a day with nothing logged yet just
         * remembers the pick until a wear/plan is actually recorded (see
         * [CalendarEventActions.logWear]/[CalendarRecommendationActions]'s
         * own reads of [pendingOccasionState]). */
        fun onOccasionSelected(occasionId: OccasionId?) {
            pendingOccasionState.value = occasionId
            viewModelScope.launch {
                val date = selectedDateState.value
                wearEventRepository.observeEvents(DateRange(date, date)).first().forEach { event ->
                    wearEventRepository.updateWear(event.copy(occasionId = occasionId))
                }
            }
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
    private val pendingOccasionState: MutableStateFlow<OccasionId?>,
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
                    occasionId = pendingOccasionState.value,
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
                        occasionId = pendingOccasionState.value,
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

/** Groups [CalendarRecommendationActions]' repository dependencies so its
 * own constructor stays under detekt's `LongParameterList` threshold — the
 * same "bag of repositories" pattern `StylingContextDependencies` (core:data)
 * already established. */
internal class CalendarRecommendationDependencies(
    val stylingEngineRepository: StylingEngineRepository,
    val outfitRepository: OutfitRepository,
    val wearEventRepository: WearEventRepository,
)

/** Groups the live, currently-observed values [CalendarRecommendationActions]
 * needs to read at the moment a recommendation is requested/planned — same
 * "bag" reasoning as [CalendarRecommendationDependencies]. `internal` (not
 * `public`) since it holds an [ReferenceContext], itself `internal`. */
internal class CalendarRecommendationLiveState(
    val selectedDateState: MutableStateFlow<LocalDate>,
    val pendingOccasionState: MutableStateFlow<OccasionId?>,
    val weatherSnapshotState: MutableStateFlow<WeatherSnapshot?>,
    val referenceState: MutableStateFlow<ReferenceContext>,
)

/** M20 Part 6/9/10/11 — "recommend for this date", "show another", "plan
 * this outfit", and "replace a planned outfit with a new recommendation".
 * Every generation call goes through [CalendarRecommendationDependencies
 * .stylingEngineRepository] — the exact M19 engine — never a second one. */
class CalendarRecommendationActions
    internal constructor(
        private val dependencies: CalendarRecommendationDependencies,
        private val liveState: CalendarRecommendationLiveState,
        private val scope: CoroutineScope,
        private val clock: Clock,
        private val today: LocalDate,
        private val queryRange: DateRange,
        private val state: MutableStateFlow<DayRecommendationUiState>,
        private val toastMessage: MutableStateFlow<String?>,
    ) {
        private var requestedCount = DEFAULT_SUGGESTION_COUNT
        private var lastSignature: Set<GarmentId>? = null
        private var lastScored: ScoredOutfit? = null
        private var replacingEvent: WearEvent? = null

        fun onRequestRecommendation() {
            replacingEvent = null
            requestedCount = DEFAULT_SUGGESTION_COUNT
            lastSignature = null
            generate(isFreshRequest = true)
        }

        fun onReplaceEvent(eventId: Long) {
            scope.launch {
                val event =
                    dependencies.wearEventRepository
                        .observeEvents(queryRange)
                        .first()
                        .firstOrNull { it.id.value == eventId } ?: return@launch
                replacingEvent = event
                requestedCount = DEFAULT_SUGGESTION_COUNT
                lastSignature = null
                generate(isFreshRequest = true)
            }
        }

        fun onShowAnother() {
            requestedCount = (requestedCount + RECOMMENDATION_COUNT_STEP).coerceAtMost(MAX_RECOMMENDATION_COUNT)
            generate(isFreshRequest = false)
        }

        fun onCancel() {
            replacingEvent = null
            lastSignature = null
            lastScored = null
            state.value = DayRecommendationUiState.Idle
        }

        fun onPlanRecommendedOutfit() {
            val scored = lastScored ?: return
            val date = liveState.selectedDateState.value
            val eventBeingReplaced = replacingEvent
            scope.launch {
                val request =
                    DayPlanRequest(
                        scored = scored,
                        date = date,
                        occasionId = liveState.pendingOccasionState.value,
                        replacingEvent = eventBeingReplaced,
                    )
                persistDayRecommendation(dependencies, request, today, clock)
                toastMessage.value = if (eventBeingReplaced != null) "Outfit replaced" else "Planned for $date"
                replacingEvent = null
                lastSignature = null
                lastScored = null
                state.value = DayRecommendationUiState.Idle
            }
        }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun generate(isFreshRequest: Boolean) {
            val previousState = state.value
            scope.launch {
                state.value = DayRecommendationUiState.Loading
                try {
                    val date = liveState.selectedDateState.value
                    val context =
                        SuggestionContext(
                            date = date,
                            weather = liveState.weatherSnapshotState.value,
                            occasionId = liveState.pendingOccasionState.value,
                        )
                    val results = dependencies.stylingEngineRepository.suggestOutfits(context, requestedCount)
                    val next =
                        if (isFreshRequest) {
                            results.firstOrNull()
                        } else {
                            results.firstOrNull { it.garmentSignature() != lastSignature }
                        }
                    if (next == null) {
                        toastMessage.value = NO_MORE_ALTERNATIVES_MESSAGE
                        state.value = previousState
                        return@launch
                    }
                    lastScored = next
                    lastSignature = next.garmentSignature()
                    val garmentsById =
                        liveState.referenceState.value.garments
                            .associateBy { it.id }
                    state.value =
                        DayRecommendationUiState.Loaded(
                            model = next.toDayRecommendationUiModel(garmentsById),
                            replacingEventId = replacingEvent?.id?.value,
                        )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    state.value = DayRecommendationUiState.Error(RECOMMENDATION_FAILED_MESSAGE)
                }
            }
        }
    }

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}

private data class UiMappingContext(
    val garmentsById: Map<GarmentId, Garment>,
    val outfitsById: Map<OutfitId, Outfit>,
    val occasions: List<Occasion>,
    val costPerWearByGarmentId: Map<GarmentId, CostPerWearEntry>,
)

private fun buildCalendarUiState(
    events: List<WearEvent>,
    reference: ReferenceContext,
    inputs: UiInputs,
    today: LocalDate,
    conflicts: List<CalendarConflict>,
    extra: ExtraContext,
): CalendarUiState {
    val context =
        UiMappingContext(
            garmentsById = reference.garments.associateBy { it.id },
            outfitsById = reference.outfits.associateBy { it.id },
            occasions = reference.occasions,
            costPerWearByGarmentId = extra.costPerWear.associateBy { it.garmentId },
        )
    val eventsByDate = events.groupBy { it.date }
    val conflictDates = conflicts.map { it.date }.toSet()

    val monthDays = buildMonthDays(inputs.visibleMonth, eventsByDate, today, conflictDates)
    val selectedDayRawEvents = eventsByDate[inputs.selectedDate].orEmpty()
    val selectedDayEvents = selectedDayRawEvents.map { it.toUiModel(context, today) }
    val selectedDayConflictMessages = conflicts.filter { it.date == inputs.selectedDate }.map { it.message }
    val selectedDayOccasionId = inputs.pendingOccasionId ?: selectedDayRawEvents.firstNotNullOfOrNull { it.occasionId }
    val historyByMonth = buildHistoryByMonth(inputs.viewMode, events, context, today)

    return CalendarUiState(
        isLoading = false,
        viewMode = inputs.viewMode,
        visibleMonth = inputs.visibleMonth,
        monthDays = monthDays,
        selectedDate = inputs.selectedDate,
        selectedDayEvents = selectedDayEvents,
        historyByMonth = historyByMonth,
        savedOutfits = buildSavedOutfitOptions(reference.outfits, context.garmentsById),
        closetGarments = reference.garments.map { it.toTileUiModel() },
        toastMessage = inputs.toast,
        selectedDayConflictMessages = selectedDayConflictMessages,
        availableOccasions = reference.occasions,
        selectedDayOccasionId = selectedDayOccasionId,
        selectedDateWeather = extra.weather,
    )
}

private fun buildHistoryByMonth(
    viewMode: CalendarViewMode,
    events: List<WearEvent>,
    context: UiMappingContext,
    today: LocalDate,
): List<MonthHistoryGroup> {
    if (viewMode != CalendarViewMode.LIST) return emptyList()
    return events
        .filter { it.status == WearEventStatus.WORN }
        .sortedByDescending { it.date }
        .groupBy { YearMonth.from(it.date) }
        .map { (month, monthEvents) -> MonthHistoryGroup(month, monthEvents.map { it.toUiModel(context, today) }) }
}

private fun buildSavedOutfitOptions(
    outfits: List<Outfit>,
    garmentsById: Map<GarmentId, Garment>,
): List<SimpleOutfitOption> =
    outfits.filter { it.isSaved && !it.isArchived }.map { outfit ->
        SimpleOutfitOption(
            id = outfit.id.value,
            name = outfit.name?.takeUnless { it.isBlank() } ?: "Untitled look",
            thumbnailPath =
                outfit.garments.sortedBy { it.layerSlot }.firstNotNullOfOrNull { slot ->
                    garmentsById[slot.garmentId]?.images?.firstOrNull { it.type == ImageType.THUMBNAIL }?.filePath
                },
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
    context: UiMappingContext,
    today: LocalDate,
): WearEventUiModel {
    val garment = garmentId?.let { context.garmentsById[it] }
    val outfit = outfitId?.let { context.outfitsById[it] }
    val outfitThumbnail =
        outfit?.garments?.sortedBy { it.layerSlot }?.firstNotNullOfOrNull { slot ->
            context.garmentsById[slot.garmentId]
                ?.images
                ?.firstOrNull { it.type == ImageType.THUMBNAIL }
                ?.filePath
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
        occasionName = occasionId?.let { occ -> context.occasions.firstOrNull { it.id == occ }?.name },
        wornRecently = computeWornRecently(this, context.outfitsById, context.costPerWearByGarmentId, today),
    )
}
