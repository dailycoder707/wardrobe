package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.tryon.GarmentMask
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10 — manual erase/restore masks (see [GarmentMask]'s KDoc for why
 * these are stored in the garment cutout's own pixel space, shared across
 * every placement template for that garment). Entirely user-drawn; nothing
 * in this repository or its implementation performs auto-segmentation.
 */
interface GarmentMaskRepository {
    fun observeGarmentMask(
        bodyProfileId: BodyProfileId,
        garmentId: GarmentId,
    ): Flow<GarmentMask?>

    suspend fun saveGarmentMask(mask: GarmentMask)

    suspend fun clearGarmentMask(
        bodyProfileId: BodyProfileId,
        garmentId: GarmentId,
    )
}
