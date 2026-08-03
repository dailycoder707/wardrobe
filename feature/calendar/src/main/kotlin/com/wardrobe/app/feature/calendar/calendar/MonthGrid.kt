package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private const val COLUMNS = 7

/** `docs/design/screen-specifications.md` §10's month grid — day number plus a
 * wear-dot indicator (dots, not thumbnails, per that spec). Gold dot = worn,
 * outline dot = planned; both can show on the same day. */
@Composable
fun MonthGrid(
    days: List<DayCellUiModel>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        WeekdayHeader()
        days.chunked(COLUMNS).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isSelected = day.date == selectedDate,
                        onClick = { onSelectDate(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = LocalLocale.current.platformLocale
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    Row(modifier = Modifier.fillMaxWidth()) {
        for (offset in 0 until COLUMNS) {
            val day = firstDayOfWeek.plus(offset.toLong())
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: DayCellUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .semantics { contentDescription = dayCellDescription(day) }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = dayNumberColor(day, isSelected),
            )
            if (day.hasActivity || day.hasConflict) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (day.wornCount > 0) WearDot(filled = true, isSelected = isSelected)
                    if (day.plannedCount > 0) WearDot(filled = false, isSelected = isSelected)
                    if (day.hasConflict) ConflictDot()
                }
            }
        }
    }
}

private fun dayCellDescription(day: DayCellUiModel): String =
    buildString {
        append(day.date.dayOfMonth)
        if (day.isToday) append(", today")
        if (day.wornCount > 0) append(", ${day.wornCount} logged")
        if (day.plannedCount > 0) append(", ${day.plannedCount} planned")
        if (day.hasConflict) append(", needs your attention")
    }

@Composable
private fun dayNumberColor(
    day: DayCellUiModel,
    isSelected: Boolean,
): Color =
    when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !day.isCurrentMonth -> WardrobeTheme.extendedColors.textSecondary.copy(alpha = 0.4f)
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

/** [filled] (worn) renders a solid dot; the outline case (planned) renders a
 * hollow ring, so a day can show both without them becoming visually
 * indistinguishable. */
@Composable
private fun WearDot(
    filled: Boolean,
    isSelected: Boolean,
) {
    val color = if (isSelected) MaterialTheme.colorScheme.onPrimary else WardrobeTheme.extendedColors.accent
    Box(
        modifier =
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .let { base -> if (filled) base.background(color) else base.border(1.dp, color, CircleShape) },
    )
}

/** Phase 9 — a small, subtle dot marking a day
 * `WardrobeIntelligenceRepository.observeCalendarConflicts` flagged, never a
 * badge count or a popup (Constitution: calm, not intrusive). Details live
 * in the Day Detail panel once the user taps through. */
@Composable
private fun ConflictDot() {
    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
}
