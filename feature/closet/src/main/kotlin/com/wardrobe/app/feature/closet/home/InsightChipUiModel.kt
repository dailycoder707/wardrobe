package com.wardrobe.app.feature.closet.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow

/** Pulled out of `HomeScreen.kt` specifically to keep that file's own
 * function count under detekt's `TooManyFunctions` threshold — Phase 5e's
 * "Your Style" chip row, unchanged by this move. */
internal data class InsightChipUiModel(
    val label: String,
    val value: String,
    val onClick: () -> Unit,
)

internal fun buildInsightChips(
    insights: HomeInsightsUiModel,
    onOpenGarment: (Long) -> Unit,
    onOpenInsights: () -> Unit,
): List<InsightChipUiModel> =
    listOfNotNull(
        insights.mostUsedGarment?.let { InsightChipUiModel("Most worn", it.title) { onOpenGarment(it.id) } },
        insights.waitingToBeWornGarment?.let {
            InsightChipUiModel("Waiting to be worn", it.title) { onOpenGarment(it.id) }
        },
        insights.recentlyPurchasedGarment?.let {
            InsightChipUiModel("Newest addition", it.title) { onOpenGarment(it.id) }
        },
        insights.favoriteColorName?.let { InsightChipUiModel("Favorite color", it, onOpenInsights) },
        insights.favoriteBrandName?.let { InsightChipUiModel("Favorite brand", it, onOpenInsights) },
        insights.favoriteCategoryName?.let { InsightChipUiModel("Favorite category", it, onOpenInsights) },
        insights.upcomingOutfitLabel?.let { InsightChipUiModel("Coming up", it, onOpenInsights) },
    )

private val INSIGHT_CHIP_WIDTH = 140.dp

@Composable
internal fun InsightChip(chip: InsightChipUiModel) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.width(INSIGHT_CHIP_WIDTH).wardrobeShadow(WardrobeElevation.RESTING, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        onClick = chip.onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                chip.label,
                style = MaterialTheme.typography.labelSmall,
                color = WardrobeTheme.extendedColors.textSecondary,
            )
            Text(
                chip.value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
