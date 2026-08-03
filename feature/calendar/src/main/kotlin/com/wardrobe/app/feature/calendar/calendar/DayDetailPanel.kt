package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.wear.WearEventStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DayDetailPanel(
    date: LocalDate,
    events: List<WearEventUiModel>,
    actions: DayDetailActions,
    modifier: Modifier = Modifier,
    conflictMessages: List<String> = emptyList(),
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

        conflictMessages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (events.isEmpty()) {
            Text(
                text = "Nothing logged for this day",
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            TextButton(onClick = actions.onLogWear) { Text("Log what you wore") }
        } else {
            events.forEach { event ->
                DayEventRow(
                    event = event,
                    onReschedule = { actions.onRescheduleEvent(event.id) },
                    onConfirmWorn = { actions.onConfirmWorn(event.id) },
                    onDelete = { actions.onDeleteEvent(event.id) },
                    onScheduleRecurring = { actions.onScheduleRecurring(event.sourceId) },
                )
                HorizontalDivider()
            }
            TextButton(onClick = actions.onLogWear) { Text("Log something else") }
        }
    }
}

@Composable
private fun DayEventRow(
    event: WearEventUiModel,
    onReschedule: () -> Unit,
    onConfirmWorn: () -> Unit,
    onDelete: () -> Unit,
    onScheduleRecurring: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val thumbnailModifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        Box(modifier = thumbnailModifier) {
            if (event.thumbnailPath != null) {
                AsyncImage(
                    model = event.thumbnailPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (event.status == WearEventStatus.PLANNED) "Planned" else "Worn",
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
        if (event.status == WearEventStatus.PLANNED) {
            IconButton(onClick = onConfirmWorn) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = "Mark as worn")
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
}
