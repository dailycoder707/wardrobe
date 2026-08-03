package com.wardrobe.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius

/**
 * Marks a garment saved via Add-to-Wardrobe's "Save as Draft" path
 * ([com.wardrobe.app.core.model.garment.Garment.isReviewed] `== false`) —
 * shown on Closet grid tiles and Garment Detail, wherever such a garment
 * appears, so an incomplete draft is never silently indistinguishable from a
 * fully-entered item. Editing and saving via Edit Garment clears it.
 */
@Composable
fun NeedsReviewBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Needs Review",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(WardrobeRadius.chip))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
