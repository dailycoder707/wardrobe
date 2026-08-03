package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/** Phase 9's Home-screen additions beyond the Phase 7/8 assistant line — kept
 * in their own file (mirroring `HomeSyncConfirmation.kt`'s precedent) so
 * `HomeScreen.kt` doesn't grow past detekt's `LongMethod`/`TooManyFunctions`
 * thresholds every time Home gains a new reason to change. Every card here
 * is plain and tappable, never a popup/dialog (Constitution: calm, not
 * intrusive) — absent entirely, not shown empty, when there's nothing to say. */
@Composable
internal fun TodaysOccasionLine(occasionName: String?) {
    if (occasionName == null) return
    Text(
        text = "Today: $occasionName",
        style = MaterialTheme.typography.bodyMedium,
        color = WardrobeTheme.extendedColors.textSecondary,
    )
}

@Composable
internal fun WardrobeHealthScoreCard(
    healthScore: Int?,
    rotationScore: Int?,
    onClick: () -> Unit,
) {
    if (healthScore == null) return
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScoreColumn(value = healthScore, label = "Wardrobe Health")
            rotationScore?.let { ScoreColumn(value = it, label = "Rotation Score") }
        }
    }
}

@Composable
private fun ScoreColumn(
    value: Int,
    label: String,
) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.displayMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
    }
}

@Composable
internal fun AttentionItemsCard(
    count: Int,
    onClick: () -> Unit,
) {
    if (count <= 0) return
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$count item${if (count == 1) "" else "s"} could use some attention",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun UpcomingTripReminderLine(
    reminder: String?,
    onClick: () -> Unit,
) {
    if (reminder == null) return
    Text(
        text = reminder,
        style = MaterialTheme.typography.bodyMedium,
        color = WardrobeTheme.extendedColors.textSecondary,
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}

@Composable
internal fun LaundryReminderLine(count: Int) {
    if (count <= 0) return
    Text(
        text = "$count item${if (count == 1) "" else "s"} in the laundry",
        style = MaterialTheme.typography.bodyMedium,
        color = WardrobeTheme.extendedColors.textSecondary,
    )
}
