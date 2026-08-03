package com.wardrobe.app.feature.outfits.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeRadius
import com.wardrobe.app.core.designsystem.theme.WardrobeTheme
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

private val PRIMARY_SLOTS = listOf(OutfitSlot.OUTERWEAR, OutfitSlot.TOP, OutfitSlot.BOTTOM, OutfitSlot.SHOES)
private val ACCESSORY_SLOTS = listOf(OutfitSlot.BAG, OutfitSlot.JEWELRY, OutfitSlot.WATCH, OutfitSlot.ACCESSORIES)
private const val EMPTY_SLOT_ICON_ALPHA = 0.6f

/** Groups a slot tile's callbacks so [OutfitSlotTile] stays under detekt's
 * LongParameterList threshold. */
private data class SlotTileActions(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
    val onPositioned: (Rect) -> Unit,
)

fun OutfitSlot.displayLabel(): String =
    when (this) {
        OutfitSlot.TOP -> "Top / Dress"
        OutfitSlot.BOTTOM -> "Bottom"
        OutfitSlot.DRESS -> "Top / Dress"
        OutfitSlot.OUTERWEAR -> "Outerwear"
        OutfitSlot.SHOES -> "Shoes"
        OutfitSlot.BAG -> "Bag"
        OutfitSlot.JEWELRY -> "Jewelry"
        OutfitSlot.WATCH -> "Watch"
        OutfitSlot.ACCESSORIES -> "Accessories"
    }

/**
 * The outfit canvas — `docs/design/screen-specifications.md` §8's "Outerwear /
 * Top / Bottom / Shoes" primary column plus a smaller accessories row, in
 * dressing order top-to-bottom (the same order `motion-guide.md`'s outfit
 * reveal animates in). [OutfitSlot.DRESS] shares the Top/Dress visual slot
 * with [OutfitSlot.TOP] — the ViewModel already enforces they're never both
 * filled — so [slots] is looked up under both keys for that position.
 */
@Composable
fun OutfitCanvas(
    slots: Map<OutfitSlot, GarmentTileUiModel>,
    onSlotTap: (OutfitSlot) -> Unit,
    onSlotLongPress: (OutfitSlot) -> Unit,
    onSlotPositioned: (OutfitSlot, Rect) -> Unit,
    draggingOverSlot: OutfitSlot?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PRIMARY_SLOTS.forEach { slot ->
                val isDressSlot = slot == OutfitSlot.TOP && slots.containsKey(OutfitSlot.DRESS)
                val effectiveSlot = if (isDressSlot) OutfitSlot.DRESS else slot
                OutfitSlotTile(
                    slot = slot,
                    garment = slots[effectiveSlot],
                    isDropTarget = draggingOverSlot == slot,
                    actions =
                        SlotTileActions(
                            onTap = { onSlotTap(effectiveSlot) },
                            onLongPress = { onSlotLongPress(effectiveSlot) },
                            onPositioned = { rect -> onSlotPositioned(slot, rect) },
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCESSORY_SLOTS.forEach { slot ->
                OutfitSlotTile(
                    slot = slot,
                    garment = slots[slot],
                    isDropTarget = draggingOverSlot == slot,
                    actions =
                        SlotTileActions(
                            onTap = { onSlotTap(slot) },
                            onLongPress = { onSlotLongPress(slot) },
                            onPositioned = { rect -> onSlotPositioned(slot, rect) },
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OutfitSlotTile(
    slot: OutfitSlot,
    garment: GarmentTileUiModel?,
    isDropTarget: Boolean,
    actions: SlotTileActions,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(WardrobeRadius.tile)
    val glowColor by androidx.compose.animation.animateColorAsState(
        targetValue =
            if (isDropTarget) {
                WardrobeTheme.extendedColors.accent.copy(alpha = 0.4f)
            } else {
                Color.Transparent
            },
        label = "slotDropGlow",
    )
    val description =
        if (garment != null) "${slot.displayLabel()}: ${garment.title}" else "${slot.displayLabel()}, empty"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val emptySlotColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        val backgroundColor = if (garment == null) emptySlotColor else MaterialTheme.colorScheme.surface
        val tileModifier =
            modifier
                .aspectRatio(1f)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    actions.onPositioned(Rect(position, coordinates.size.toSize()))
                }.clip(shape)
                .background(backgroundColor)
                .let { base -> if (garment != null) base.wardrobeShadow(WardrobeElevation.RESTING, shape) else base }
                .background(glowColor, shape)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }.combinedClickable(onClick = actions.onTap, onLongClick = actions.onLongPress)
        Box(
            modifier = tileModifier,
            contentAlignment = Alignment.Center,
        ) {
            SlotThumbnailOrIcon(garment)
        }
        Text(
            text = slot.displayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = WardrobeTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SlotThumbnailOrIcon(garment: GarmentTileUiModel?) {
    if (garment?.thumbnailPath != null) {
        val thumbnailModifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp)
                .clip(RoundedCornerShape(WardrobeRadius.thumbnailInset))
        AsyncImage(
            model = garment.thumbnailPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = thumbnailModifier,
        )
    } else if (garment != null) {
        Icon(
            imageVector = Icons.Outlined.Checkroom,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Checkroom,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.alpha(EMPTY_SLOT_ICON_ALPHA),
        )
    }
}
