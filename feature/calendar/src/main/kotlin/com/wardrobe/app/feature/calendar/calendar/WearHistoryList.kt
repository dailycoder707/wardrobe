package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/** `docs/design/screen-specifications.md` §11 — reverse-chronological, grouped
 * by month, row = date + thumbnail + occasion tag. Reached only via
 * Calendar's List toggle, over the same underlying [WearEventUiModel] data
 * the month grid uses — not a separate data source. */
@Composable
fun WearHistoryList(
    groups: List<MonthHistoryGroup>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        groups.forEach { group ->
            item(key = "header_${group.month}") {
                Text(
                    text = group.month.atDay(1).format(MONTH_FORMATTER),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(group.events, key = { it.id }) { event -> WearHistoryRow(event) }
        }
    }
}

@Composable
private fun WearHistoryRow(event: WearEventUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val weekday = event.date.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale)
        Text(
            text = "$weekday ${event.date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
            modifier = Modifier.size(48.dp),
        )
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
        Text(text = event.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (event.occasionName != null) {
            Text(
                text = event.occasionName,
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}
