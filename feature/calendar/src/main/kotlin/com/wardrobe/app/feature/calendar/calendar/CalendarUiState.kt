package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.runtime.Immutable
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
)

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
)
