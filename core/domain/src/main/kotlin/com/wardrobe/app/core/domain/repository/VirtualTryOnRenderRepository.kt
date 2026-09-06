package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.tryon.VirtualTryOnRenderOutcome

/**
 * M12's Try-On Review seam — `feature:tryon` depends on this interface only,
 * never on `core:ai`'s `VirtualTryOnEngine` directly (feature modules only
 * ever see `core:domain` repository interfaces, per this project's layering
 * rule). The implementation (`core:data`) decodes the two file paths,
 * dispatches through `VirtualTryOnEngine`/`TryOnRouter`, and writes the
 * result to a scratch preview file — this call never overwrites either
 * input file; Try-On is preview only.
 */
interface VirtualTryOnRenderRepository {
    /** [forceOnDevice] lets the Try-On Review screen's comparison viewer
     * request the on-device render explicitly, alongside the normal
     * auto-routed call (`forceOnDevice = false`, whatever the user's
     * configured provider produces) — the returned outcome's own `source`
     * always reflects what actually ran, so the screen never has to assume
     * a cloud call happened just because one wasn't forced off. */
    suspend fun render(
        bodyPhotoPath: String,
        garmentCutoutPath: String,
        maskPath: String? = null,
        forceOnDevice: Boolean = false,
    ): VirtualTryOnRenderOutcome
}
