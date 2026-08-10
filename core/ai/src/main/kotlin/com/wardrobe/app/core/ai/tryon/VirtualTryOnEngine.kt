package com.wardrobe.app.core.ai.tryon

import android.graphics.Bitmap
import com.wardrobe.app.core.model.ai.AiResultSource

/**
 * Add-to-Wardrobe v2's fifth capability (M12). Deliberately typed in terms
 * of plain [Bitmap]s rather than `core:tryon`'s own render-pipeline types:
 * this interface must not force `core:ai` to depend on `core:tryon`.
 * Instead `core:tryon` depends on `core:ai` to *implement* this interface
 * (`OnDeviceVirtualTryOnEngine`, wrapping the existing `BodyAnchorEstimator`
 * → `DefaultPlacementCalculator` → `LightingMatcher` → `ShadowRenderer`
 * pipeline unchanged) — the dependency only ever points one way.
 *
 * [mask] (M12) is the optional garment-mask override
 * `core:tryon`'s `GarmentMaskEditor` already produces — `null` renders the
 * garment cutout's own alpha channel as-is, the same behavior as before this
 * parameter existed (every pre-M12 call site still compiles unchanged).
 */
interface VirtualTryOnEngine {
    suspend fun render(
        bodyPhoto: Bitmap,
        garmentCutout: Bitmap,
        mask: Bitmap? = null,
    ): TryOnRenderResult
}

sealed interface TryOnRenderResult {
    data class Success(
        val renderedImage: Bitmap,
        val confidence: Float?,
        val source: AiResultSource,
    ) : TryOnRenderResult

    data class Failure(
        val reason: String,
    ) : TryOnRenderResult
}
