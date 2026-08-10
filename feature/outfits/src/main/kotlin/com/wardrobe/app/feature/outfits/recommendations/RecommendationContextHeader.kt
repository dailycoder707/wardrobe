package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

/** "Today's recommendation" header (M19 Part 4): the real weather this run's
 * scoring actually used ([RecommendationsUiState.weatherSummary], `null`
 * only when genuinely unavailable), and a real occasion selector reusing
 * the existing `Occasion` reference data — selecting one recomputes for
 * real (see [RecommendationsViewModel.onOccasionSelected]), it doesn't just
 * relabel the current outfit. */
@Composable
internal fun TodaysContextHeader(
    state: RecommendationsUiState,
    onOccasionSelected: (OccasionId?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today's recommendation", style = MaterialTheme.typography.titleLarge)
        Text(
            state.weatherSummary ?: "Weather details aren't available.",
            style = MaterialTheme.typography.bodyMedium,
            color = WardrobeTheme.extendedColors.textSecondary,
        )
        if (state.availableOccasions.isNotEmpty()) {
            OccasionSelector(state.availableOccasions, state.selectedOccasionId, onOccasionSelected)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccasionSelector(
    occasions: List<Occasion>,
    selectedOccasionId: OccasionId?,
    onOccasionSelected: (OccasionId?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WardrobeFilterChip(
            label = "Any occasion",
            selected = selectedOccasionId == null,
            onClick = { onOccasionSelected(null) },
        )
        occasions.forEach { occasion ->
            WardrobeFilterChip(
                label = occasion.name,
                selected = selectedOccasionId == occasion.id,
                onClick = { onOccasionSelected(if (selectedOccasionId == occasion.id) null else occasion.id) },
            )
        }
    }
}

/** The engine's real reasons ([RecommendedOutfitUiModel.reasonBullets]),
 * itemized instead of read as one paragraph — see that field's KDoc for why
 * this is the same string, never invented copy. */
@Composable
internal fun WhyThisSection(reasonBullets: List<String>) {
    if (reasonBullets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Why this?", style = MaterialTheme.typography.titleSmall)
        reasonBullets.forEach { reason ->
            Text("• $reason", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
