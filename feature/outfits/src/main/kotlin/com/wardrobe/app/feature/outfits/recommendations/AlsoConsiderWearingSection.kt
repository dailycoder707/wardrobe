package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme

/**
 * Phase 9 — every concurrent accessory/jewelry pick the engine recommends
 * beyond the single item already shown in the outfit's own slot list (a
 * necklace *and* a bracelet *and* a ring, all at once) — presentation-only,
 * kept in its own file per this package's existing "one composable group per
 * file" convention (`RecommendedItemCard.kt`).
 */
@Composable
internal fun AlsoConsiderWearingSection(items: List<RecommendedAccessoryUiModel>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Also consider wearing", style = MaterialTheme.typography.titleMedium)
        items.forEach { item ->
            Text(
                text = "${item.garmentName} — ${item.explanation}",
                style = MaterialTheme.typography.bodyMedium,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
        }
    }
}
