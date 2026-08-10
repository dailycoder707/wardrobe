package com.wardrobe.app.core.ai.privacy

import android.graphics.Bitmap
import com.wardrobe.app.core.image.pipeline.ImageResizer
import javax.inject.Inject
import javax.inject.Singleton

/** Cloud calls don't need a full 2048px-long-edge working image — capped
 * smaller to reduce upload size/cost, independent of the on-device pipeline
 * resolution. */
private const val CLOUD_PAYLOAD_LONG_EDGE_PX = 1024

/**
 * The mandatory pre-flight every image the [com.wardrobe.app.core.ai.gateway.AiGateway]
 * sends anywhere runs through first (ADR-012 §2) — not optional, not
 * bypassable per-call. Two entry points, matching the two situations a
 * cloud call can be in:
 *
 * - [prepareExtractionPayload]: the *only* capability whose cloud call needs
 *   the pre-extraction photo (with a person still in it) — face-blurred
 *   first, since Extraction's whole job is removing everything but the
 *   garment and a face is exactly the kind of unnecessary personal detail
 *   that shouldn't need to leave the device just to get there.
 * - [prepareGarmentPayload]: every other capability (Reconstruction,
 *   Metadata, Styling, Try-On) always receives the already-extracted,
 *   already-faceless garment cutout — face-blurring is a structural no-op
 *   here, not a skipped step.
 *
 * "Strip EXIF" isn't a step performed here: a decoded in-memory [Bitmap] has
 * no EXIF block to begin with (that's JPEG file metadata, gone once
 * decoded) — the real guarantee is that whatever re-encodes these bitmaps
 * into the bytes an HTTP call actually sends (the Gateway/provider adapters,
 * not this class) never copies source EXIF back in. "Crop to garment
 * bounds" happens naturally as a side effect of already-extracted payloads
 * being pre-cropped cutouts; the one case with no crop to apply is the very
 * first Extraction call, which by definition doesn't have a mask yet.
 */
interface PrivacyPreprocessor {
    suspend fun prepareExtractionPayload(originalPhoto: Bitmap): Bitmap

    suspend fun prepareGarmentPayload(cutout: Bitmap): Bitmap
}

@Singleton
class DefaultPrivacyPreprocessor
    @Inject
    constructor(
        private val faceBlurrer: FaceBlurrer,
    ) : PrivacyPreprocessor {
        override suspend fun prepareExtractionPayload(originalPhoto: Bitmap): Bitmap {
            val blurred = faceBlurrer.blurFaces(originalPhoto)
            return ImageResizer.resizeToLongEdge(blurred, CLOUD_PAYLOAD_LONG_EDGE_PX)
        }

        override suspend fun prepareGarmentPayload(cutout: Bitmap): Bitmap =
            ImageResizer.resizeToLongEdge(cutout, CLOUD_PAYLOAD_LONG_EDGE_PX)
    }
