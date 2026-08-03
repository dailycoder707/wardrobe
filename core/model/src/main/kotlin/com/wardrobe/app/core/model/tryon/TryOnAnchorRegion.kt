package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory

/**
 * Phase 10 — which [BodyMeasurements] landmark a garment's default
 * placement is anchored to. A finer classification than [OutfitSlot] alone
 * provides, the same "finer classification on top of `OutfitSlot`'s
 * catch-all" relationship [AccessoryCategory]/[JewelryCategory] already
 * have to [OutfitSlot.ACCESSORIES]/[OutfitSlot.JEWELRY] — `ACCESSORIES`
 * alone can't distinguish a belt (waist) from a scarf (neck), and
 * `JEWELRY` alone can't distinguish a ring (finger) from a necklace (neck).
 *
 * Fidelity is not uniform across regions — see
 * `phase-10-personal-virtual-tryon.md`'s category-feasibility table:
 * [SHOULDER_LINE]/[FULL_TORSO] are the most reliably estimated by on-device
 * pose detection, [WRIST]/[FINGER]/[EARLOBE] the least, and [ANKLE] is
 * hardest of all because selfie camera-to-feet perspective is the least
 * controlled variable this feature ever composites against.
 */
enum class TryOnAnchorRegion {
    SHOULDER_LINE,
    WAIST_LINE,
    HIP_LINE,
    NECK,
    WRIST,
    FINGER,
    EARLOBE,
    ANKLE,
    FULL_TORSO,
    ;

    companion object {
        /** [slot] alone decides tops/bottoms/dresses/outerwear/shoes/bags;
         * [accessory]/[jewelry] refine [OutfitSlot.ACCESSORIES]/[JEWELRY]
         * into the specific region a belt/scarf/ring/necklace/etc. anchors
         * to. Never throws — an unrecognized combination falls back to
         * [FULL_TORSO], the safest "somewhere on the body" default. */
        fun classify(
            slot: OutfitSlot,
            accessory: AccessoryCategory? = null,
            jewelry: JewelryCategory? = null,
        ): TryOnAnchorRegion =
            when (slot) {
                OutfitSlot.TOP, OutfitSlot.DRESS, OutfitSlot.OUTERWEAR -> SHOULDER_LINE
                OutfitSlot.BOTTOM -> WAIST_LINE
                OutfitSlot.SHOES -> ANKLE
                OutfitSlot.BAG -> HIP_LINE
                OutfitSlot.WATCH -> WRIST
                OutfitSlot.JEWELRY -> jewelry.toAnchorRegion()
                OutfitSlot.ACCESSORIES -> accessory.toAnchorRegion()
            }

        private fun JewelryCategory?.toAnchorRegion(): TryOnAnchorRegion =
            when (this) {
                JewelryCategory.NECKLACE -> NECK
                JewelryCategory.BRACELET -> WRIST
                JewelryCategory.RING -> FINGER
                JewelryCategory.EARRING -> EARLOBE
                JewelryCategory.OTHER, null -> FULL_TORSO
            }

        private fun AccessoryCategory?.toAnchorRegion(): TryOnAnchorRegion =
            when (this) {
                AccessoryCategory.BELT -> WAIST_LINE
                AccessoryCategory.SCARF -> NECK
                AccessoryCategory.HAIR_ACCESSORY -> EARLOBE
                AccessoryCategory.SUNGLASSES, AccessoryCategory.OTHER, null -> FULL_TORSO
            }
    }
}
