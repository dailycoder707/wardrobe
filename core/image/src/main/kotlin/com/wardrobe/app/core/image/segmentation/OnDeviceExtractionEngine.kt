package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The default/fallback [GarmentExtractionEngine] (Add-to-Wardrobe v2,
 * ADR-012) — composes the existing [BackgroundRemover] with the new
 * [PersonRegionMasker] refinement. This is what every capture runs when no
 * cloud provider is configured/consented/reachable for
 * `GARMENT_EXTRACTION`, and it's the automatic fallback whenever cloud
 * dispatch fails.
 */
@Singleton
class OnDeviceExtractionEngine
    @Inject
    constructor(
        private val backgroundRemover: BackgroundRemover,
        private val personRegionMasker: PersonRegionMasker,
    ) : GarmentExtractionEngine {
        override suspend fun extract(sourcePhoto: Bitmap): ExtractionResult =
            when (val cutout = backgroundRemover.removeBackground(sourcePhoto)) {
                is CutoutResult.Success -> {
                    val masked = personRegionMasker.mask(cutout.bitmap, sourcePhoto)
                    ExtractionResult.Success(transparentCutout = masked, confidence = cutout.confidence)
                }

                is CutoutResult.Failure -> {
                    ExtractionResult.Failure(cutout.reason)
                }
            }
    }
