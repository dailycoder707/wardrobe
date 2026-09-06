package com.wardrobe.app.feature.closet.closet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.ui.components.WardrobeFilterChip

/** Real, derived-from-actual-data wardrobe stats (M17 Part 7I) — shown only
 * when browsing the whole closet (no active search/filter), tapping one
 * applies its [ClosetInsight.filterToApply] the same way any other filter
 * chip does. Never an "AI picked"/"Perfect match" style label — see
 * [computeClosetInsights]'s own doc comment. */
@Composable
fun ClosetInsightsRow(
    insights: List<ClosetInsight>,
    onInsightClick: (ClosetFilterState) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(insights, key = { it.label }) { insight ->
            WardrobeFilterChip(
                label = insight.label,
                selected = false,
                onClick = { onInsightClick(insight.filterToApply) },
                contentDescriptionOverride = "${insight.label}. Tap to filter.",
            )
        }
    }
}
