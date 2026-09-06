package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val ALPHA_SHIFT_BITS = 24
private const val RGB_MASK = 0x00FFFFFF
private const val MAX_ALPHA = 255

/** Below this confidence, a pixel is fully transparent; above it, fully
 * opaque — only the band between is a soft ramp, matching ML Kit's own
 * anti-aliased edge behavior for the region that's genuinely ambiguous.
 * M25 real-device finding: without this curve, [hardenAlpha] would leave
 * every pixel at ML Kit's raw per-pixel confidence value, which means a
 * garment region ML Kit is only moderately sure about (dark fabric, a busy
 * print, a garment close in tone to its background) renders semi-
 * transparent in the saved cutout even though it's well inside the
 * garment's own silhouette, not near an edge at all. */
private const val ALPHA_FULL_CONFIDENCE = 0.75f
private const val ALPHA_ZERO_CONFIDENCE = 0.25f

/**
 * The provisional default chosen this phase — see phase-5b-image-pipeline.md's
 * "Background removal" section and `TECHNICAL_DEBT.md` for why this is a
 * reasoned choice rather than a spike-verified one. Nothing outside this file
 * and [BackgroundRemoverModule] knows which implementation is bound
 * (ADR-008) — swapping to a bundled TFLite model later doesn't touch
 * `GarmentImagePipeline` or anything above it.
 */
@Singleton
class MlKitBackgroundRemover
    @Inject
    constructor() : BackgroundRemover {
        private val segmenter by lazy {
            val options =
                SubjectSegmenterOptions
                    .Builder()
                    .enableForegroundBitmap()
                    .enableForegroundConfidenceMask()
                    .build()
            SubjectSegmentation.getClient(options)
        }

        override suspend fun removeBackground(bitmap: Bitmap): CutoutResult =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                segmenter
                    .process(input)
                    .addOnSuccessListener { result ->
                        val foreground = result.foregroundBitmap
                        if (foreground == null) {
                            continuation.resume(CutoutResult.Failure("mlkit_no_foreground_bitmap"))
                            return@addOnSuccessListener
                        }
                        val mask = result.foregroundConfidenceMask
                        val hardened = mask?.let { hardenAlpha(foreground, it) } ?: foreground
                        val confidence = mask?.let(::averageConfidence)
                        continuation.resume(CutoutResult.Success(hardened, confidence))
                    }.addOnFailureListener { exception ->
                        continuation.resume(
                            CutoutResult.Failure(exception.message ?: "mlkit_segmentation_failed"),
                        )
                    }
            }

        /** A real average of ML Kit's own per-pixel confidence mask — never a
         * fabricated number (Constitution rule 4). */
        private fun averageConfidence(mask: FloatBuffer): Float {
            val duplicate = mask.duplicate()
            duplicate.rewind()
            var sum = 0f
            var count = 0
            while (duplicate.hasRemaining()) {
                sum += duplicate.get()
                count++
            }
            return if (count == 0) 0f else sum / count
        }
    }

/** Rebuilds [foreground]'s alpha channel from ML Kit's own real per-pixel
 * confidence mask, mapped through [alphaCurve] — RGB values are untouched,
 * only alpha changes, so nothing about the garment's actual pixels is
 * altered. `null`/[foreground] unchanged (never a guessed/partial rebuild)
 * if the mask's element count doesn't match the bitmap's pixel count — a
 * defensive guard against an ML Kit contract change, not an expected path. */
internal fun hardenAlpha(
    foreground: Bitmap,
    mask: FloatBuffer,
): Bitmap {
    val width = foreground.width
    val height = foreground.height
    val duplicate = mask.duplicate().apply { rewind() }
    if (duplicate.remaining() != width * height) return foreground

    val pixels = IntArray(width * height)
    foreground.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        val alpha = alphaCurve(duplicate.get(index))
        pixels[index] = (pixels[index] and RGB_MASK) or (alpha shl ALPHA_SHIFT_BITS)
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

/** [ALPHA_ZERO_CONFIDENCE]/[ALPHA_FULL_CONFIDENCE] bound the one genuinely
 * ambiguous band; everything outside it maps to a hard 0 or 255 rather than
 * carrying ML Kit's raw confidence straight through to alpha. */
internal fun alphaCurve(confidence: Float): Int {
    val range = ALPHA_FULL_CONFIDENCE - ALPHA_ZERO_CONFIDENCE
    val normalized = ((confidence - ALPHA_ZERO_CONFIDENCE) / range).coerceIn(0f, 1f)
    return (normalized * MAX_ALPHA).roundToInt()
}
