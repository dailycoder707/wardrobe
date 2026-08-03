package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.outfit.OutfitSlot

/**
 * Phase 10 — a coarser front-to-back layering order than [OutfitSlot]
 * alone provides, so a jacket composites in front of the shirt it's worn
 * over rather than relying on incidental list order (the exact z-order
 * weakness `TECHNICAL_DEBT.md` item 11 already flags in
 * `OutfitPreviewScreen`'s plain list-iteration order — this fixes it
 * forward rather than repeating it). Render order sorts by
 * `(ClothingDepth.ordinal, OutfitSlot.ordinal)`, depth first.
 *
 * [INNER] has no current category that classifies into it — nothing in
 * this schema models a true base/under-layer distinct from [OutfitSlot.TOP]
 * yet. It's defined now for correctness/extensibility, not faked as
 * already meaningful; see `phase-10-personal-virtual-tryon.md`'s Known
 * Limitations.
 */
enum class ClothingDepth {
    INNER,
    NORMAL,
    OUTER,
    ;

    companion object {
        /** A static mapping, not a persisted per-garment override in v1 —
         * only [OutfitSlot.OUTERWEAR] is ever [OUTER]; everything else
         * worn on the torso is [NORMAL]. */
        fun defaultDepthFor(slot: OutfitSlot): ClothingDepth =
            when (slot) {
                OutfitSlot.OUTERWEAR -> OUTER
                else -> NORMAL
            }
    }
}
