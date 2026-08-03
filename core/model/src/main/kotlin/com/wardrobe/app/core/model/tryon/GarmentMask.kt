package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import java.time.Instant

/**
 * A manual erase/restore mask over a garment's own cutout — entirely
 * user-drawn, no auto-segmentation ("no AI for this step" per the brief).
 * Stored **in the garment cutout's own local pixel space**, not in
 * body-photo space, so a mask stays correct regardless of which
 * [GarmentPlacementTemplate] is active or how the layer is currently
 * transformed: it means "erase this part of the cutout itself" (e.g. the
 * part a sleeve would show through hair or a held bag), applied *before*
 * the placement transform — not "erase this part of the composed photo."
 * One mask per `(bodyProfileId, garmentId)`, shared across every placement
 * template for that garment.
 */
data class GarmentMask(
    val bodyProfileId: BodyProfileId,
    val garmentId: GarmentId,
    val maskFilePath: String,
    val updatedAt: Instant,
)
