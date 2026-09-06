package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import java.time.Instant
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
                    // M25 real-device finding: neither the raw ML Kit mask
                    // nor the pose-landmark punch-out above is followed by
                    // any cleanup for disconnected fragments (a severed
                    // sleeve tip, a stray hair strand) — see
                    // CutoutFragmentCleanup.kt's own KDoc for the full
                    // root-cause trace.
                    val cleaned = keepLargestOpaqueComponent(masked)
                    ExtractionResult.Success(
                        transparentCutout = cleaned,
                        confidence = cutout.confidence,
                        provenance = onDeviceProvenance(),
                    )
                }

                is CutoutResult.Failure -> {
                    ExtractionResult.Failure(cutout.reason)
                }
            }
    }

private fun onDeviceProvenance(): AiResultProvenance =
    AiResultProvenance(
        source = AiResultSource.ON_DEVICE,
        provider = null,
        model = null,
        promptVersion = null,
        generatedAt = Instant.now(),
    )
