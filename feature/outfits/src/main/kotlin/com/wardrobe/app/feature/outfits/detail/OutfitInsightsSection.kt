package com.wardrobe.app.feature.outfits.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.model.intelligence.ComfortLevel
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.intelligence.OutfitRating
import com.wardrobe.app.core.model.intelligence.WarmthLevel

/** Phase 9's Outfit Detail additions — Average Rating (derived from Phase
 * 6's Feedback votes, never a second rating concept), Suitable Weather,
 * Estimated Comfort/Warmth, Rotation Priority. Kept in its own file, the
 * same "sibling file over a growing screen file" precedent
 * `OutfitMetadataSection`/`WearHistorySection` already use in this package. */
@Composable
internal fun OutfitInsightsSection(insights: OutfitInsights) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InsightStat(label = "Average Rating", value = ratingLabel(insights.averageRating))
            InsightStat(label = "Rotation Priority", value = insights.rotationPriority?.toString() ?: "—")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            InsightStat(label = "Estimated Comfort", value = comfortLabel(insights.estimatedComfort))
            InsightStat(label = "Estimated Warmth", value = warmthLabel(insights.estimatedWarmth))
        }
        if (insights.suitableWeather.isNotEmpty()) {
            InsightChipLine(
                label = "Suitable Weather",
                values = insights.suitableWeather.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            )
        }
    }
}

private fun ratingLabel(rating: OutfitRating?): String =
    if (rating == null) {
        "Not yet rated"
    } else {
        "%.1f/5 (%d votes)".format(rating.stars, rating.totalVotes)
    }

private fun comfortLabel(level: ComfortLevel): String =
    when (level) {
        ComfortLevel.RELAXED -> "Relaxed"
        ComfortLevel.MODERATE -> "Moderate"
        ComfortLevel.STRUCTURED -> "Structured"
    }

private fun warmthLabel(level: WarmthLevel): String =
    when (level) {
        WarmthLevel.LIGHT -> "Light"
        WarmthLevel.MODERATE -> "Moderate"
        WarmthLevel.WARM -> "Warm"
    }

@Composable
private fun InsightStat(
    label: String,
    value: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
    }
}

@Composable
private fun InsightChipLine(
    label: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = WardrobeTheme.extendedColors.textSecondary)
        Text(values.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
    }
}
