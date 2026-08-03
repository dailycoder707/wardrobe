package com.wardrobe.app.core.model.tryon

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.GarmentPlacementTemplateId
import java.time.Instant

/** Named placement variants a garment can have against one body profile —
 * `CASUAL`/`FORMAL` are user-facing presets, `CUSTOM` lets the user save as
 * many additional named variants as they like (see [GarmentPlacementTemplate.customName]). */
enum class PlacementTemplateType {
    DEFAULT,
    CASUAL,
    FORMAL,
    CUSTOM,
}

/**
 * One saved translate/scale/rotate transform for a garment against a body
 * profile — **affine only**: no body-contour warping, no cloth simulation,
 * no perspective mesh deformation (see `phase-10-personal-virtual-tryon.md`'s
 * core technical decision for why). Persisted per `(bodyProfileId,
 * garmentId, templateType, customName)`, not per-outfit — the same garment
 * appears in the same position everywhere it's tried on, with several
 * named variants coexisting rather than one placement per outfit.
 *
 * [offsetXFraction]/[offsetYFraction] are fractions of the body reference
 * photo's own width/height (the same 0..1 convention `NormalizedRect`
 * already uses). [isUserAdjusted] starts `false` for an auto-seeded
 * template and flips to `true` permanently on the user's first manual
 * drag/pinch/rotate — Constitution rule 7's "editable suggestion, never a
 * fact" made concrete: an un-adjusted template's UI shows an "Auto-placed
 * — drag to adjust" chip that disappears forever once adjusted, and can
 * only be brought back by an explicit "Reset to Auto Placement" action.
 */
data class GarmentPlacementTemplate(
    val id: GarmentPlacementTemplateId,
    val bodyProfileId: BodyProfileId,
    val garmentId: GarmentId,
    val templateType: PlacementTemplateType,
    val customName: String?,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val isUserAdjusted: Boolean,
    val placementSource: MeasurementSource,
    val lastUsedAt: Instant?,
)
