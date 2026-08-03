package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.GarmentPlacementTemplateId
import com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10 — saved affine placements for a garment against a body profile
 * (see [GarmentPlacementTemplate]'s KDoc for why several named variants can
 * coexist per garment). The implementation, not this interface, resolves
 * which [com.wardrobe.app.core.model.tryon.TryOnAnchorRegion] a garment
 * anchors to (from its own category), so callers never need to pass one in.
 */
interface TryOnPlacementRepository {
    fun observeGarmentPlacementTemplates(
        bodyProfileId: BodyProfileId,
        garmentId: GarmentId,
    ): Flow<List<GarmentPlacementTemplate>>

    suspend fun saveGarmentPlacementTemplate(template: GarmentPlacementTemplate): GarmentPlacementTemplateId

    suspend fun deleteGarmentPlacementTemplate(id: GarmentPlacementTemplateId)

    /** Bumps `lastUsedAt` to now — "the last used template becomes default"
     * (see [defaultTemplateFor]) is resolved purely from this column. */
    suspend fun markTemplateUsed(id: GarmentPlacementTemplateId)

    /** Never returns `null`: the row with the greatest `lastUsedAt`, else the
     * `DEFAULT`-typed row, else a freshly computed (and persisted) `DEFAULT`
     * template seeded by `DefaultPlacementCalculator` — a garment with no
     * saved template yet still gets a placement to render with. */
    suspend fun defaultTemplateFor(
        bodyProfileId: BodyProfileId,
        garmentId: GarmentId,
    ): GarmentPlacementTemplate

    /** The "Reset to Auto Placement" button's backing call — recomputes
     * [templateId]'s offset/scale/rotation from the current body
     * measurements and clears `isUserAdjusted`, without touching its
     * `templateType`/`customName`/`lastUsedAt`. Takes the exact row (rather
     * than a `(bodyProfileId, garmentId, templateType)` triple) so it stays
     * unambiguous for `CUSTOM` templates, which are only disambiguated by
     * `customName`. */
    suspend fun resetToAutoPlacement(templateId: GarmentPlacementTemplateId): GarmentPlacementTemplate
}
