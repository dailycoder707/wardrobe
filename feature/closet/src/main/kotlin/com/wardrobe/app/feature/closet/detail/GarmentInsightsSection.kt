package com.wardrobe.app.feature.closet.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/* Phase 9 — GarmentMetadata's derived-stats rows, split into their own
 * file purely to stay under detekt's TooManyFunctions ceiling on
 * GarmentMetadataSection.kt. Reuses that file's internal MetadataRow/
 * ChipRow helpers. */

/** "Worn 4 days ago" — never hardcoded, always derived from
 * [GarmentInsights.lastWornDate] against today. */
private fun lastWornLabel(insights: GarmentInsights): String {
    val lastWorn = insights.lastWornDate ?: return "Never worn"
    val days = ChronoUnit.DAYS.between(lastWorn, LocalDate.now())
    return when {
        days <= 0 -> "Worn today"
        days == 1L -> "Worn yesterday"
        else -> "Worn $days days ago"
    }
}

private fun firstWornLabel(insights: GarmentInsights): String =
    insights.firstWornDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Not yet worn"

@Composable
internal fun GarmentWearStatsRow(insights: GarmentInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock(label = "Last Worn", value = lastWornLabel(insights))
            StatBlock(label = "Total Wears", value = insights.totalWears.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock(label = "First Worn", value = firstWornLabel(insights))
            StatBlock(
                label = "Cost Per Wear",
                value = insights.costPerWear?.let { "%.2f".format(it) } ?: "—",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock(
                label = "Rotation Score",
                value = insights.rotationScore?.toString() ?: "Not enough history yet",
            )
            StatBlock(
                label = "Wear Frequency",
                value = "%.1f/month".format(insights.wearFrequencyPerMonth),
            )
        }
    }
}

@Composable
internal fun GarmentSeasonUsageRow(insights: GarmentInsights) {
    if (insights.seasonUsage.isEmpty()) return
    ChipRow(
        label = "Season Usage",
        values =
            insights.seasonUsage.entries.sortedByDescending { it.value }.map { (season, count) ->
                "${season.name.lowercase().replaceFirstChar(Char::uppercase)} ($count)"
            },
    )
}

@Composable
internal fun GarmentAvailabilitySection(
    insights: GarmentInsights,
    onToggleLaundry: () -> Unit,
) {
    MetadataRow(
        label = "Availability",
        value =
            insights.status.name
                .lowercase()
                .replaceFirstChar(Char::uppercase),
    )
    insights.packedForTripName?.let { MetadataRow(label = "Packing Status", value = "Packed for $it") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "In Laundry",
            style = MaterialTheme.typography.bodyMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
        Switch(checked = insights.isInLaundry, onCheckedChange = { onToggleLaundry() })
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.displayMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
    }
}
