package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

private const val BOX_2D_SIZE = 4
private const val BOX_YMIN_INDEX = 0
private const val BOX_XMIN_INDEX = 1
private const val BOX_YMAX_INDEX = 2
private const val BOX_XMAX_INDEX = 3
private const val NORMALIZED_SCALE = 1000
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val RGB_MASK = 0x00FFFFFF
private const val UBYTE_MASK = 0xFF
private const val FULLY_OPAQUE = 255f

/** A real, non-fabricated cutout composited from Gemini's own per-pixel
 * segmentation output — [bitmap]'s RGB values are exactly [sourcePhoto]'s
 * original pixels, never regenerated/synthesized; only the alpha channel is
 * new, sampled directly from Gemini's own probability mask. [confidence] is
 * the mean of those sampled mask values (0-1) — a real, derived number, not
 * a placeholder. */
class GeminiSegmentationCutout(
    val bitmap: Bitmap,
    val confidence: Float,
)

/**
 * Turns Gemini's own segmentation response (a normalized bounding box + a
 * base64 PNG probability mask, both real model output — see
 * `core:data`'s `GeminiSegmentationSupport` for where these are parsed off
 * the wire) into an actual transparent-cutout [Bitmap] the same shape every
 * other [ExtractionResult.Success.transparentCutout] already is.
 *
 * Deliberately lives in `core:image`, not `core:data` or `core:ai`: it is
 * pure `Bitmap`/`Base64` pixel arithmetic with no HTTP/JSON/vendor
 * awareness of its own (the caller already resolved which vendor and
 * already decoded the JSON), mirroring this module's existing ownership of
 * [keepLargestOpaqueComponent] and every on-device segmentation step.
 *
 * Returns `null` — never a partial/guessed cutout — for any input that
 * can't honestly be turned into one: a malformed [normalizedBox], a
 * degenerate box once descaled to [sourcePhoto]'s real pixel dimensions, or
 * a [maskBase64Png] that fails to decode as a real image.
 */
fun compositeGeminiSegmentationCutout(
    sourcePhoto: Bitmap,
    normalizedBox: List<Int>,
    maskBase64Png: String,
): GeminiSegmentationCutout? =
    pixelBox(normalizedBox, sourcePhoto.width, sourcePhoto.height)?.let { box ->
        decodeMaskBitmap(maskBase64Png)?.let { decodedMask ->
            val scaledMask = Bitmap.createScaledBitmap(decodedMask, box.width, box.height, true)
            compositeCutout(sourcePhoto, scaledMask, box)
        }
    }

private class PixelBox(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** Descales Gemini's normalized `[ymin, xmin, ymax, xmax]` (0-1000) against
 * [sourcePhoto]'s real pixel dimensions. `null` for a malformed box (wrong
 * element count) or a degenerate one (zero/negative width or height once
 * descaled) — never a guessed/clamped-to-something region. */
private fun pixelBox(
    normalizedBox: List<Int>,
    sourceWidth: Int,
    sourceHeight: Int,
): PixelBox? {
    if (normalizedBox.size != BOX_2D_SIZE) return null
    val left = (normalizedBox[BOX_XMIN_INDEX].coerceIn(0, NORMALIZED_SCALE) * sourceWidth) / NORMALIZED_SCALE
    val top = (normalizedBox[BOX_YMIN_INDEX].coerceIn(0, NORMALIZED_SCALE) * sourceHeight) / NORMALIZED_SCALE
    val right = (normalizedBox[BOX_XMAX_INDEX].coerceIn(0, NORMALIZED_SCALE) * sourceWidth) / NORMALIZED_SCALE
    val bottom = (normalizedBox[BOX_YMAX_INDEX].coerceIn(0, NORMALIZED_SCALE) * sourceHeight) / NORMALIZED_SCALE
    val width = right - left
    val height = bottom - top
    return if (width <= 0 || height <= 0) null else PixelBox(left, top, width, height)
}

/** A cloud response's mask is untrusted input — malformed/truncated base64
 * or a non-image payload must degrade to `null`, the same defensive
 * contract [com.wardrobe.app.core.ai.gateway.adapter.GenericRestAdapter]'s
 * own `decodeBase64Bitmap` already established for a self-hosted backend's
 * result image. The `data:image/png;base64,` prefix Gemini's documented
 * output includes is stripped if present; a bare base64 string (no prefix)
 * passes through unchanged. */
private fun decodeMaskBitmap(maskBase64Png: String): Bitmap? =
    runCatching {
        val base64 = maskBase64Png.substringAfter(',', maskBase64Png)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

private fun compositeCutout(
    sourcePhoto: Bitmap,
    scaledMask: Bitmap,
    box: PixelBox,
): GeminiSegmentationCutout {
    val width = sourcePhoto.width
    val height = sourcePhoto.height
    val sourcePixels = IntArray(width * height)
    sourcePhoto.getPixels(sourcePixels, 0, width, 0, 0, width, height)
    val maskPixels = IntArray(box.width * box.height)
    scaledMask.getPixels(maskPixels, 0, box.width, 0, 0, box.width, box.height)

    var alphaSum = 0L
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = y * width + x
            val inBox = x in box.left until (box.left + box.width) && y in box.top until (box.top + box.height)
            val alpha = if (inBox) maskPixels[(y - box.top) * box.width + (x - box.left)].maskAlpha() else 0
            alphaSum += alpha
            sourcePixels[index] = (sourcePixels[index] and RGB_MASK) or (alpha shl ALPHA_SHIFT)
        }
    }
    val cutout =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourcePixels, 0, width, 0, 0, width, height)
        }
    val confidence = (alphaSum.toFloat() / sourcePixels.size) / FULLY_OPAQUE
    return GeminiSegmentationCutout(keepLargestOpaqueComponent(cutout), confidence)
}

/** Gemini's mask is grayscale (R=G=B by construction) — the red channel is
 * as good a probability sample as any, so no separate luminance formula is
 * needed here. */
private fun Int.maskAlpha(): Int = (this ushr RED_SHIFT) and UBYTE_MASK
