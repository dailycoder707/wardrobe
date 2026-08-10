package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap

/**
 * The Add-to-Wardrobe v2 capability interface superseding
 * [BackgroundRemover]'s direct use in [com.wardrobe.app.core.image.pipeline.GarmentImagePipeline] —
 * "extraction" means garment-only isolation (background *and* the person
 * wearing the garment removed), not just background removal. Lives in
 * `core:image`, not `core:ai`: the on-device implementation
 * (`OnDeviceExtractionEngine`, composing [BackgroundRemover] +
 * [PersonRegionMasker]) belongs next to the pipeline it feeds; `core:ai`'s
 * cloud implementation depends on this module to implement it, never the
 * reverse, so `core:image` stays cloud-unaware.
 */
interface GarmentExtractionEngine {
    suspend fun extract(sourcePhoto: Bitmap): ExtractionResult
}

sealed interface ExtractionResult {
    /** [confidence] is `null` when the implementation doesn't expose a real
     * scalar confidence — never fabricated, same convention as
     * [CutoutResult.Success]. */
    data class Success(
        val transparentCutout: Bitmap,
        val confidence: Float?,
    ) : ExtractionResult

    data class Failure(
        val reason: String,
    ) : ExtractionResult
}
