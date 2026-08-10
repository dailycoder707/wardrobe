package com.wardrobe.app.core.image.presentation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton

private const val ALPHA_OPAQUE_THRESHOLD = 32
private const val ALPHA_SHIFT_BITS = 24
private const val RED_SHIFT_BITS = 16
private const val GREEN_SHIFT_BITS = 8
private const val BYTE_MASK = 0xFF
private const val RGB_CHANNEL_COUNT = 3f
private const val CROP_PADDING_FRACTION = 0.04f
private const val TARGET_LUMINANCE = 128f
private const val MAX_WHITE_BALANCE_GAIN = 1.4f
private const val MIN_WHITE_BALANCE_GAIN = 0.7f
private const val CONTRAST_BOOST = 1.08f
private const val SHADOW_BLUR_RADIUS_FRACTION = 0.02f
private const val SHADOW_ALPHA = 70
private const val WHITE_CANVAS_MARGIN_FRACTION = 0.08f
private const val SHADOW_WIDTH_FRACTION = 0.42f
private const val SHADOW_HEIGHT_FRACTION = 0.06f

/**
 * Add-to-Wardrobe v2's cosmetic post-processing step — deliberately
 * on-device only, no cloud variant, since it's classical image processing,
 * not "AI." Never touches color, embroidery, print, or fabric texture:
 * every operation here is a uniform geometric transform (deskew/crop) or a
 * uniform per-channel linear color transform (white balance/contrast) — the
 * kind of change that can't selectively alter one part of a pattern
 * differently from another.
 *
 * Deliberately **not** attempted: full 4-point perspective/homography
 * flattening (only meaningful for flat-lay/hanger photos, and unreliable
 * contour detection without a real CV library — a small, bounded deskew
 * correction, see [deskew], is a safer default for a photo of a *worn*
 * garment) and wrinkle removal (too easy to cross into altering fabric
 * texture, which is explicitly forbidden).
 */
interface GarmentPresentationEnhancer {
    fun enhance(cutout: Bitmap): EnhancedPresentation
}

data class EnhancedPresentation(
    val enhancedCutout: Bitmap,
    val whiteBackgroundVariant: Bitmap,
)

@Singleton
class DefaultGarmentPresentationEnhancer
    @Inject
    constructor() : GarmentPresentationEnhancer {
        override fun enhance(cutout: Bitmap): EnhancedPresentation {
            val cropped = cropToOpaqueBounds(deskew(cutout))
            val colorCorrected = applyWhiteBalanceAndContrast(cropped)
            val whiteBackground = compositeOnWhiteWithShadow(colorCorrected)
            return EnhancedPresentation(enhancedCutout = colorCorrected, whiteBackgroundVariant = whiteBackground)
        }
    }

private fun computeOpaqueBounds(bitmap: Bitmap): Rect? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (index in pixels.indices) {
        if ((pixels[index] ushr ALPHA_SHIFT_BITS) < ALPHA_OPAQUE_THRESHOLD) continue
        val x = index % width
        val y = index / width
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    return if (maxX < minX || maxY < minY) null else Rect(minX, minY, maxX + 1, maxY + 1)
}

private fun cropToOpaqueBounds(bitmap: Bitmap): Bitmap {
    val cropRect = computeOpaqueBounds(bitmap)?.let { paddedCropRect(it, bitmap.width, bitmap.height) }
    return if (cropRect != null && cropRect.width() > 0 && cropRect.height() > 0) {
        Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
    } else {
        bitmap
    }
}

private fun paddedCropRect(
    bounds: Rect,
    bitmapWidth: Int,
    bitmapHeight: Int,
): Rect {
    val paddingX = (bounds.width() * CROP_PADDING_FRACTION).toInt()
    val paddingY = (bounds.height() * CROP_PADDING_FRACTION).toInt()
    return Rect(
        (bounds.left - paddingX).coerceAtLeast(0),
        (bounds.top - paddingY).coerceAtLeast(0),
        (bounds.right + paddingX).coerceAtMost(bitmapWidth),
        (bounds.bottom + paddingY).coerceAtMost(bitmapHeight),
    )
}

/** Gray-world white balance (equalize the three channel means over opaque
 * pixels only — background/transparent pixels never skew the estimate)
 * folded into the same linear transform as a mild, fixed contrast boost.
 * Both are uniform per-channel scale/offset operations — incapable of
 * selectively altering one region's color relative to another the way a
 * "change the color" edit would. */
private fun applyWhiteBalanceAndContrast(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var count = 0L
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    for (pixel in pixels) {
        if ((pixel ushr ALPHA_SHIFT_BITS) < ALPHA_OPAQUE_THRESHOLD) continue
        count++
        sumR += (pixel shr RED_SHIFT_BITS) and BYTE_MASK
        sumG += (pixel shr GREEN_SHIFT_BITS) and BYTE_MASK
        sumB += pixel and BYTE_MASK
    }
    if (count == 0L) return bitmap
    val meanR = sumR.toFloat() / count
    val meanG = sumG.toFloat() / count
    val meanB = sumB.toFloat() / count
    val grayAverage = (meanR + meanG + meanB) / RGB_CHANNEL_COUNT

    fun gain(mean: Float): Float =
        if (mean <= 0f) 1f else (grayAverage / mean).coerceIn(MIN_WHITE_BALANCE_GAIN, MAX_WHITE_BALANCE_GAIN)

    val offset = TARGET_LUMINANCE * (1f - CONTRAST_BOOST)
    val colorMatrix =
        ColorMatrix(
            floatArrayOf(
                gain(meanR) * CONTRAST_BOOST,
                0f,
                0f,
                0f,
                offset,
                0f,
                gain(meanG) * CONTRAST_BOOST,
                0f,
                0f,
                offset,
                0f,
                0f,
                gain(meanB) * CONTRAST_BOOST,
                0f,
                offset,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        )

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

/** The white-background variant is always computed locally by compositing
 * onto white — no extra cloud call needed regardless of which extraction
 * provider produced the cutout. The shadow is a soft, blurred ellipse under
 * the silhouette — clearly a synthesized presentation element, not a change
 * to the garment itself. */
private fun compositeOnWhiteWithShadow(cutout: Bitmap): Bitmap {
    val marginX = (cutout.width * WHITE_CANVAS_MARGIN_FRACTION).toInt()
    val marginY = (cutout.height * WHITE_CANVAS_MARGIN_FRACTION).toInt()
    val result = Bitmap.createBitmap(cutout.width + marginX * 2, cutout.height + marginY * 2, Bitmap.Config.ARGB_8888)
    result.eraseColor(Color.WHITE)
    val canvas = Canvas(result)

    computeOpaqueBounds(cutout)?.let { bounds ->
        val blurRadius = (cutout.width * SHADOW_BLUR_RADIUS_FRACTION).coerceAtLeast(1f)
        val shadowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(SHADOW_ALPHA, 0, 0, 0)
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        val centerX = marginX + (bounds.left + bounds.right) / 2f
        val centerY = marginY + bounds.bottom.toFloat()
        val radiusX = bounds.width() * SHADOW_WIDTH_FRACTION
        val radiusY = bounds.height() * SHADOW_HEIGHT_FRACTION
        canvas.drawOval(
            RectF(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY),
            shadowPaint,
        )
    }

    canvas.drawBitmap(cutout, marginX.toFloat(), marginY.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
    return result
}
