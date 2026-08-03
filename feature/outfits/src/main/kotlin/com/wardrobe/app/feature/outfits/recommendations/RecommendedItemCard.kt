package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow

private val THUMBNAIL_SIZE = 56.dp

@Composable
fun RecommendedItemCard(
    item: RecommendedItemUiModel,
    onClick: () -> Unit,
    onReplace: () -> Unit,
) {
    val shape = RoundedCornerShape(WardrobeRadius.tile)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .wardrobeShadow(WardrobeElevation.RESTING, shape)
                .clip(shape)
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.tile.thumbnailPath,
                contentDescription = item.tile.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(WardrobeRadius.thumbnailInset)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.slotLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = WardrobeTheme.extendedColors.textSecondary,
                )
                Text(item.tile.title, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onReplace) {
                Icon(Icons.Filled.Refresh, contentDescription = "Replace ${item.slotLabel.lowercase()}")
            }
        }
    }
}
