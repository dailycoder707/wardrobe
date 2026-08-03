package com.wardrobe.app.core.model.outfit

import com.wardrobe.app.core.model.common.GarmentId

/** The inverse of [OutfitGarmentSlot.layerSlot] — used by the 2D Outfit Preview
 * (Phase 6) to know which garment (if any) fills each named slot. */
fun Outfit.garmentsBySlot(): Map<OutfitSlot, GarmentId> =
    garments.associate { OutfitSlot.fromIndex(it.layerSlot) to it.garmentId }
