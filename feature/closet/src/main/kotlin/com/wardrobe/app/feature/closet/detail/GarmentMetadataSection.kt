package com.wardrobe.app.feature.closet.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.ui.components.NeedsReviewBadge
import com.wardrobe.app.core.ui.components.WardrobeFilterChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MAX_HISTORY_ROWS = 20

@Composable
fun GarmentMetadata(
    state: GarmentDetailUiState,
    onToggleLaundry: () -> Unit = {},
) {
    val garment = state.garment ?: return

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = garment.name ?: state.categoryName ?: "Untitled item",
            style = MaterialTheme.typography.displayMedium,
        )
        if (!garment.isReviewed) NeedsReviewBadge()

        GarmentBasicInfoRows(garment, state.categoryName, state.brandName)
        GarmentChipSections(garment)

        state.insights?.let { insights ->
            GarmentWearStatsRow(insights)
            GarmentSeasonUsageRow(insights)
            GarmentAvailabilitySection(insights, onToggleLaundry)
        }

        garment.notes?.let { GarmentNotesSection(label = "Notes", notes = it) }
        garment.careNotes?.let { GarmentNotesSection(label = "Care Notes", notes = it) }
        if (state.wearHistory.isNotEmpty()) GarmentWearHistorySection(state.wearHistory)
    }
}

@Composable
private fun GarmentBasicInfoRows(
    garment: Garment,
    categoryName: String?,
    brandName: String?,
) {
    MetadataRow(label = "Category", value = categoryName ?: "—")
    brandName?.let { MetadataRow(label = "Brand", value = it) }
    if (garment.materials.isNotEmpty()) {
        MetadataRow(
            label = "Material",
            value = garment.materials.joinToString { "${it.material.name} (${it.percentage}%)" },
        )
    }
    if (garment.palette.isNotEmpty()) {
        MetadataRow(label = "Color", value = garment.palette.joinToString { it.color.name })
    }
    garment.size?.let { MetadataRow(label = "Size", value = it) }
    garment.price?.let { MetadataRow(label = "Price", value = "${it.amount} ${it.currencyCode}") }
    garment.condition?.let {
        MetadataRow(label = "Condition", value = it.name.lowercase().replaceFirstChar(Char::uppercase))
    }
}

@Composable
private fun GarmentChipSections(garment: Garment) {
    if (garment.seasons.isNotEmpty()) {
        ChipRow(
            label = "Season",
            values = garment.seasons.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        )
    }
    if (garment.dressCodes.isNotEmpty()) {
        ChipRow(
            label = "Dress Code",
            values =
                garment.dressCodes.map {
                    it.name
                        .lowercase()
                        .replace('_', ' ')
                        .replaceFirstChar(Char::uppercase)
                },
        )
    }
}

@Composable
private fun GarmentNotesSection(
    label: String,
    notes: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GarmentWearHistorySection(wearHistory: List<LocalDate>) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    Column {
        Text("Wear History", style = MaterialTheme.typography.titleMedium)
        wearHistory.take(MAX_HISTORY_ROWS).forEach { date ->
            Text(
                date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun MetadataRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = WardrobeTheme.extendedColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ChipRow(
    label: String,
    values: List<String>,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> WardrobeFilterChip(label = value, selected = false, onClick = {}) }
        }
    }
}
