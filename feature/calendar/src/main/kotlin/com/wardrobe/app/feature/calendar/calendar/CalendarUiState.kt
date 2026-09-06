package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarViewMode { CALENDAR, LIST }

@Immutable
data class WearEventUiModel(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val thumbnailPath: String?,
    val isOutfit: Boolean,
    /** The referenced garment or outfit id (whichever [isOutfit] points to) —
     * distinct from [id], the `WearEvent` row's own id. "Repeat weekly"
     * schedules more of *this outfit*, not more of this one logged row. */
    val sourceId: Long,
    val status: WearEventStatus,
    val occasionName: String?,
    /** M20 — real wear-history signal from [com.wardrobe.app.core.domain.repository.StatsRepository]:
     * `true` when any garment this event references was genuinely logged as
     * [WearEventStatus.WORN] within the recent-wear window, `false` when
     * none were, `null` when there's nothing to check against (no garments
     * resolved, e.g. a reference to a deleted item). Never guessed. */
    val wornRecently: Boolean? = null,
)

/** M20 — Part 8's three honestly-distinct weather states for the selected
 * calendar date, never a single nullable summary that conflates "not asked
 * yet", "genuinely unavailable", and "too far out to forecast". */
sealed interface DateWeatherUiState {
    /** No snapshot resolved at all — weather is off in Settings, no location
     * is configured, or the provider genuinely returned nothing. */
    data object Unavailable : DateWeatherUiState

    /** A snapshot was returned, but its own [com.wardrobe.app.core.model.weather.WeatherSnapshot.date]
     * doesn't match the requested date — the repository's cache-fallback
     * path returned some *other* day's data rather than genuine data for
     * this one (e.g. a date beyond the provider's forecast window). Shown
     * distinctly so it's never mistaken for a real forecast. */
    data object ForecastUnavailableForDate : DateWeatherUiState

    data class Available(
        val summary: String,
    ) : DateWeatherUiState
}

/** M20 Part 6/9/10 — a single candidate outfit from
 * [com.wardrobe.app.core.domain.repository.StylingEngineRepository],
 * re-presented for Calendar's compact Day Detail context. Deliberately not
 * the full multi-tab [com.wardrobe.app.core.model.styling.ScoredOutfit] card
 * `feature:outfits` renders — this is a smaller surface, not a duplicate of it. */
@Immutable
data class DayRecommendationUiModel(
    val title: String,
    val explanation: String,
    val thumbnails: List<String?>,
    /** `null` for a rule-engine outfit — mirrors [com.wardrobe.app.core.model.styling.ScoredOutfit.provenance]
     * exactly; when non-null the outfit was genuinely cloud-styled. */
    val providerLabel: String?,
    val confidencePercent: Int?,
    val isCacheHit: Boolean,
)

sealed interface DayRecommendationUiState {
    data object Idle : DayRecommendationUiState

    data object Loading : DayRecommendationUiState

    /** [replacingEventId] is `null` for a fresh "Get recommendation" on an
     * empty day, or the id of the existing planned [WearEventUiModel] this
     * recommendation would replace — the same [DayRecommendationUiModel]
     * either way, only "Plan this outfit"'s persistence path differs. */
    data class Loaded(
        val model: DayRecommendationUiModel,
        val replacingEventId: Long?,
    ) : DayRecommendationUiState

    data class Error(
        val message: String,
    ) : DayRecommendationUiState
}

@Immutable
data class DayCellUiModel(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val wornCount: Int,
    val plannedCount: Int,
    /** Phase 9 — a subtle badge only, never a popup/dialog (Constitution:
     * calm, not intrusive) — see `WardrobeIntelligenceRepository
     * .observeCalendarConflicts` for what counts as a conflict (duplicate
     * planned outfit, a planned garment currently in the laundry or packed
     * for a different trip). */
    val hasConflict: Boolean = false,
) {
    val hasActivity: Boolean get() = wornCount > 0 || plannedCount > 0
}

@Immutable
data class MonthHistoryGroup(
    val month: YearMonth,
    val events: List<WearEventUiModel>,
)

@Immutable
data class SimpleOutfitOption(
    val id: Long,
    val name: String,
    val thumbnailPath: String?,
)

@Immutable
data class CalendarUiState(
    val isLoading: Boolean = true,
    val viewMode: CalendarViewMode = CalendarViewMode.CALENDAR,
    val visibleMonth: YearMonth = YearMonth.now(),
    val monthDays: List<DayCellUiModel> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDayEvents: List<WearEventUiModel> = emptyList(),
    val historyByMonth: List<MonthHistoryGroup> = emptyList(),
    val savedOutfits: List<SimpleOutfitOption> = emptyList(),
    val closetGarments: List<GarmentTileUiModel> = emptyList(),
    val toastMessage: String? = null,
    /** Phase 9 — conflict messages for [selectedDate], if any. */
    val selectedDayConflictMessages: List<String> = emptyList(),
    /** M20 — real reference data for the occasion picker; never a second,
     * parallel Occasion concept. */
    val availableOccasions: List<Occasion> = emptyList(),
    /** M20 — the occasion assigned to [selectedDate], derived from that
     * day's own [WearEvent][com.wardrobe.app.core.model.wear.WearEvent]s
     * when any exist, or the user's not-yet-persisted pick otherwise. */
    val selectedDayOccasionId: OccasionId? = null,
    val selectedDateWeather: DateWeatherUiState = DateWeatherUiState.Unavailable,
    /** M22 fix: this combine chain previously had no error boundary at
     * all — a throwing repository flow would leave the screen stuck on its
     * initial `isLoading = true` state forever instead of surfacing an
     * honest, retryable error, unlike Recommendations' own `isError` state
     * for the same class of failure. */
    val error: String? = null,
)
