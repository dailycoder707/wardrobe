package com.wardrobe.app.feature.stats.common.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

private val CELL_SIZE = 12.dp
private val CELL_SPACING = 3.dp
private const val DAYS_PER_WEEK = 7
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** A GitHub-style contribution grid answering "when did I actually get
 * dressed" at a glance — one column per week, oldest on the left, today's
 * partial week on the right. Deliberately no per-cell numbers (that's what
 * tapping a cell's content description / [onDayClick] is for) — the whole
 * point of a heatmap is the shape, not the digits. */
@Composable
fun CalendarHeatmap(
    cells: List<HeatmapCellUiModel>,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val countsByDate = cells.associate { it.date to it.wearCount }
    val maxCount = cells.maxOfOrNull { it.wearCount }?.coerceAtLeast(1) ?: 1
    val firstDayOfWeek = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
    val weeks = buildHeatmapWeeks(rangeStart, rangeEnd, firstDayOfWeek)

    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
        items(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
                week.forEach { date ->
                    HeatmapCell(date, countsByDate[date] ?: 0, maxCount, onDayClick)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    date: LocalDate?,
    count: Int,
    maxCount: Int,
    onDayClick: (LocalDate) -> Unit,
) {
    val intensity = if (date == null) 0f else (count.toFloat() / maxCount).coerceIn(0f, 1f)
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor =
        if (intensity == 0f) {
            baseColor
        } else {
            WardrobeTheme.extendedColors.accent.copy(alpha = 0.25f + intensity * 0.75f)
        }
    val description = if (date == null) "" else "${date.format(DATE_FORMATTER)}: $count worn"

    val cellModifier =
        Modifier
            .size(CELL_SIZE)
            .clip(RoundedCornerShape(2.dp))
            .background(fillColor)
            .semantics { contentDescription = description }
            .let { base -> if (date != null) base.clickable { onDayClick(date) } else base }
    Box(modifier = cellModifier)
}

private fun buildHeatmapWeeks(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    firstDayOfWeek: DayOfWeek,
): List<List<LocalDate?>> {
    val leadingBlankDays = ((rangeStart.dayOfWeek.value - firstDayOfWeek.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val gridStart = rangeStart.minusDays(leadingBlankDays.toLong())
    val totalDays = ChronoUnit.DAYS.between(gridStart, rangeEnd).toInt() + 1
    val totalCells = ((totalDays + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK) * DAYS_PER_WEEK

    val allDays =
        (0 until totalCells).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            date.takeIf { !it.isBefore(rangeStart) && !it.isAfter(rangeEnd) }
        }
    return allDays.chunked(DAYS_PER_WEEK)
}
