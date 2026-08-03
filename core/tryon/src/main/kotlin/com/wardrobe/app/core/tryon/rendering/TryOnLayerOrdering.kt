package com.wardrobe.app.core.tryon.rendering

import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.tryon.ClothingDepth

/**
 * Sorts try-on layers back-to-front by `(ClothingDepth.ordinal, OutfitSlot.ordinal)`,
 * depth first — a coarser front-to-back order than [OutfitSlot] alone
 * provides, so e.g. a jacket composites in front of the shirt it's worn
 * over rather than relying on incidental list order. This fixes forward
 * (rather than repeats) the z-order weakness `TECHNICAL_DEBT.md` item 11
 * already flags in `OutfitPreviewScreen`'s plain list-iteration order.
 *
 * A pure function over any caller-supplied item type (not tied to a
 * specific UI model) so it stays unit-testable without Android or Compose.
 */
fun <T> sortForRender(
    items: List<T>,
    slotOf: (T) -> OutfitSlot,
): List<T> =
    items.sortedWith(
        compareBy(
            { ClothingDepth.defaultDepthFor(slotOf(it)).ordinal },
            { slotOf(it).ordinal },
        ),
    )
