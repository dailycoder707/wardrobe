package com.wardrobe.app.core.image.presentation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val ALPHA_OPAQUE_THRESHOLD = 32
private const val ALPHA_SHIFT_BITS = 24
private const val RED_SHIFT_BITS = 16
private const val GREEN_SHIFT_BITS = 8
private const val BYTE_MASK = 0xFF
private const val CROP_PADDING_FRACTION = 0.04f
private const val TARGET_LUMINANCE = 128f
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
 * uniform, identical-across-every-pixel contrast boost — the kind of change
 * that can't selectively alter one part of a pattern differently from
 * another. No per-channel white balance is applied here (M25 real-device
 * finding — see [applyWhiteBalanceAndContrast]'s own KDoc for why gray-world
 * balancing over an isolated garment cutout was actively wrong, not merely
 * unnecessary).
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

/** M25 real-device finding: this used to also apply gray-world white balance
 * — equalizing the three channel means over the garment's own opaque pixels
 * toward a shared gray average. That's the wrong domain for gray-world:
 * the assumption a gray-world correction depends on ("the scene's average
 * reflectance is neutral gray") is true of a whole photographed scene, not
 * of a single already-isolated, often strongly-colored garment. Applied to
 * the cutout alone, it read a garment's own real hue as a "color cast" and
 * pulled every channel toward the others — a saturated red top measurably
 * desaturated toward gray on every enhance() call, the opposite of
 * "preserve original garment color." Only the contrast boost survives,
 * pivoted at [TARGET_LUMINANCE] so it can't clip highlights the way the old
 * per-channel gain (up to 1.4x, multiplied into a pivot computed for gain=1)
 * did — see the file's own git history for the old formula.
 *
 * Direct per-pixel arithmetic, not `Canvas`/`ColorMatrixColorFilter` (M25
 * real-device follow-up): identical math, but real, deterministic integer
 * arithmetic this project can actually unit-test — Robolectric's `Canvas`
 * shadow does not apply a `ColorMatrixColorFilter` at all, which is what let
 * the original gray-world bug ship unnoticed (the color-based assertion
 * silently degenerated to comparing two zeros under test, always passing
 * regardless of what the real transform did). */
private fun applyWhiteBalanceAndContrast(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val offset = TARGET_LUMINANCE * (1f - CONTRAST_BOOST)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val alpha = (pixel ushr ALPHA_SHIFT_BITS) and BYTE_MASK
        val red = contrastChannel((pixel shr RED_SHIFT_BITS) and BYTE_MASK, offset)
        val green = contrastChannel((pixel shr GREEN_SHIFT_BITS) and BYTE_MASK, offset)
        val blue = contrastChannel(pixel and BYTE_MASK, offset)
        pixels[index] = (alpha shl ALPHA_SHIFT_BITS) or (red shl RED_SHIFT_BITS) or (green shl GREEN_SHIFT_BITS) or blue
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun contrastChannel(
    value: Int,
    offset: Float,
): Int = ((value * CONTRAST_BOOST) + offset).roundToInt().coerceIn(0, BYTE_MASK)

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
