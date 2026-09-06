package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.wear.WearEventStatus
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Suppress("LongParameterList")
@Composable
fun DayDetailPanel(
    date: LocalDate,
    events: List<WearEventUiModel>,
    actions: DayDetailActions,
    modifier: Modifier = Modifier,
    conflictMessages: List<String> = emptyList(),
    availableOccasions: List<Occasion> = emptyList(),
    selectedOccasionId: OccasionId? = null,
    weather: DateWeatherUiState = DateWeatherUiState.Unavailable,
) {
    val today = LocalDate.now()
    val heading =
        when {
            date == today -> "Today's outfit"
            date.isAfter(today) -> "Planned for ${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        }

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(heading, style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = actions.onDuplicateDay) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate day")
                }
                IconButton(onClick = actions.onClearDay, enabled = events.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear day")
                }
            }
        }

        WeatherLine(weather)
        OccasionChipRow(
            occasions = availableOccasions,
            selectedOccasionId = selectedOccasionId,
            onOccasionSelected = actions.onOccasionSelected,
        )

        conflictMessages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (events.isEmpty()) {
            DayDetailEmptyState(onGetRecommendation = actions.onGetRecommendation, onChooseOutfit = actions.onLogWear)
        } else {
            events.forEach { event ->
                DayEventRow(
                    event = event,
                    onReschedule = { actions.onRescheduleEvent(event.id) },
                    onConfirmWorn = { actions.onConfirmWorn(event.id) },
                    onDelete = { actions.onDeleteEvent(event.id) },
                    onScheduleRecurring = { actions.onScheduleRecurring(event.sourceId) },
                    onReplace = { actions.onReplaceEvent(event.id) },
                )
                HorizontalDivider()
            }
            TextButton(onClick = actions.onLogWear) { Text("Log something else") }
        }
    }
}

@Composable
private fun WeatherLine(weather: DateWeatherUiState) {
    val text =
        when (weather) {
            is DateWeatherUiState.Available -> weather.summary
            DateWeatherUiState.Unavailable -> "Weather unavailable"
            DateWeatherUiState.ForecastUnavailableForDate -> "Forecast unavailable for this date"
        }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = WardrobeTheme.extendedColors.textSecondary)
}

/** Part 7 — one occasion per day, picked from the real [Occasion] reference
 * table (never a second Occasion concept). "No occasion" always leads so a
 * day can be explicitly cleared, not just left in whatever state it started. */
@Composable
private fun OccasionChipRow(
    occasions: List<Occasion>,
    selectedOccasionId: OccasionId?,
    onOccasionSelected: (OccasionId?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            WardrobeFilterChip(
                label = "No occasion",
                selected = selectedOccasionId == null,
                onClick = { onOccasionSelected(null) },
            )
        }
        items(occasions, key = { it.id.value }) { occasion ->
            WardrobeFilterChip(
                label = occasion.name,
                selected = selectedOccasionId == occasion.id,
                onClick = { onOccasionSelected(occasion.id) },
            )
        }
    }
}

/** Part 4's honest empty state — never a generic placeholder card, and
 * distinct from [com.wardrobe.app.core.ui.components.EmptyState] (which
 * assumes a full-screen host); this panel is embedded inside a scrollable
 * calendar layout, so it stays a wrap-content Column instead. */
@Composable
private fun DayDetailEmptyState(
    onGetRecommendation: () -> Unit,
    onChooseOutfit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Checkroom,
            contentDescription = null,
            tint = WardrobeTheme.extendedColors.accent,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(text = "No outfit planned", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = "Get an outfit recommendation, or choose one yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGetRecommendation) { Text("Get recommendation") }
            OutlinedButton(onClick = onChooseOutfit) { Text("Choose outfit") }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun DayEventRow(
    event: WearEventUiModel,
    onReschedule: () -> Unit,
    onConfirmWorn: () -> Unit,
    onDelete: () -> Unit,
    onScheduleRecurring: () -> Unit,
    onReplace: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DayEventThumbnail(event.thumbnailPath)
        DayEventInfo(event, modifier = Modifier.weight(1f))
        DayEventActionsRow(event, onReschedule, onConfirmWorn, onDelete, onScheduleRecurring, onReplace)
    }
}

@Composable
private fun DayEventThumbnail(thumbnailPath: String?) {
    val thumbnailModifier =
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    Box(modifier = thumbnailModifier) {
        if (thumbnailPath != null) {
            AsyncImage(
                model = thumbnailPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Icon(imageVector = Icons.Outlined.Checkroom, contentDescription = null, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun DayEventInfo(
    event: WearEventUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(event.title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (event.status == WearEventStatus.PLANNED) "Planned" else "Worn",
            style = MaterialTheme.typography.labelSmall,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
        event.wornRecently?.let { wornRecently ->
            val accentColor = WardrobeTheme.extendedColors.accent
            val secondaryColor = WardrobeTheme.extendedColors.textSecondary
            Text(
                text = if (wornRecently) "Worn recently" else "Not worn recently",
                style = MaterialTheme.typography.labelSmall,
                color = if (wornRecently) accentColor else secondaryColor,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun DayEventActionsRow(
    event: WearEventUiModel,
    onReschedule: () -> Unit,
    onConfirmWorn: () -> Unit,
    onDelete: () -> Unit,
    onScheduleRecurring: () -> Unit,
    onReplace: () -> Unit,
) {
    if (event.status == WearEventStatus.PLANNED) {
        IconButton(onClick = onConfirmWorn) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = "Mark as worn")
        }
    }
    if (event.isOutfit && event.status == WearEventStatus.PLANNED) {
        IconButton(onClick = onReplace) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Replace with a new recommendation")
        }
    }
    if (event.isOutfit) {
        IconButton(onClick = onScheduleRecurring) {
            Icon(Icons.Outlined.EventRepeat, contentDescription = "Repeat this look weekly")
        }
    }
    IconButton(onClick = onReschedule) {
        Icon(Icons.Outlined.Schedule, contentDescription = "Reschedule")
    }
    IconButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = "Remove")
    }
}
