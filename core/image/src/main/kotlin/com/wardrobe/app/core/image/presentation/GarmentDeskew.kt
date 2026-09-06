package com.wardrobe.app.core.image.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2

private const val ALPHA_OPAQUE_THRESHOLD = 32
private const val ALPHA_SHIFT_BITS = 24
private const val PCA_HALF_ANGLE_FACTOR = 0.5
private const val UPRIGHT_DEGREES = 90f
private const val HALF_ROTATION_DEGREES = 180f
private const val DESKEW_ANALYSIS_LONG_EDGE_PX = 256
private const val MIN_OPAQUE_PIXELS_FOR_DESKEW = 100
private const val MAX_DESKEW_DEGREES = 20f
private const val MIN_DESKEW_DEGREES = 0.5f

private data class OpaqueMoments(
    val sumXX: Double,
    val sumYY: Double,
    val sumXY: Double,
)

/** Rotates [bitmap] upright by a small, bounded correction (see
 * [MAX_DESKEW_DEGREES]) — a phone photo is rarely rotated far off-upright,
 * so this corrects hand-holding tilt rather than attempting to reorient an
 * unknown garment pose. The angle is a real 2D covariance/PCA calculation
 * over the alpha mask's opaque pixels (Constitution rule 4 — not a
 * fabricated angle), computed on a downscaled analysis copy since the angle
 * itself is scale-invariant. */
internal fun deskew(bitmap: Bitmap): Bitmap = rotate(bitmap, computeDeskewDegrees(bitmap))

private fun computeDeskewDegrees(bitmap: Bitmap): Float {
    val analysis = scaleDownForAnalysis(bitmap, DESKEW_ANALYSIS_LONG_EDGE_PX)
    val correction = opaqueMoments(analysis)?.let(::normalizedUprightCorrection) ?: 0f
    return if (abs(correction) in MIN_DESKEW_DEGREES..MAX_DESKEW_DEGREES) correction else 0f
}

private fun opaqueMoments(bitmap: Bitmap): OpaqueMoments? {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val centroid = opaqueCentroid(pixels, width)
    return if (centroid == null) null else covariance(pixels, width, centroid.first, centroid.second)
}

private fun opaqueCentroid(
    pixels: IntArray,
    width: Int,
): Pair<Double, Double>? {
    var count = 0L
    var sumX = 0.0
    var sumY = 0.0
    for (index in pixels.indices) {
        if ((pixels[index] ushr ALPHA_SHIFT_BITS) < ALPHA_OPAQUE_THRESHOLD) continue
        count++
        sumX += index % width
        sumY += index / width
    }
    return if (count < MIN_OPAQUE_PIXELS_FOR_DESKEW) null else (sumX / count) to (sumY / count)
}

private fun covariance(
    pixels: IntArray,
    width: Int,
    meanX: Double,
    meanY: Double,
): OpaqueMoments {
    var sumXX = 0.0
    var sumYY = 0.0
    var sumXY = 0.0
    for (index in pixels.indices) {
        if ((pixels[index] ushr ALPHA_SHIFT_BITS) < ALPHA_OPAQUE_THRESHOLD) continue
        val dx = (index % width) - meanX
        val dy = (index / width) - meanY
        sumXX += dx * dx
        sumYY += dy * dy
        sumXY += dx * dy
    }
    return OpaqueMoments(sumXX, sumYY, sumXY)
}

private fun normalizedUprightCorrection(moments: OpaqueMoments): Float {
    val majorAxisDegrees =
        Math
            .toDegrees(PCA_HALF_ANGLE_FACTOR * atan2(2 * moments.sumXY, moments.sumXX - moments.sumYY))
            .toFloat()
    var correction = UPRIGHT_DEGREES - majorAxisDegrees
    while (correction > UPRIGHT_DEGREES) correction -= HALF_ROTATION_DEGREES
    while (correction <= -UPRIGHT_DEGREES) correction += HALF_ROTATION_DEGREES
    return correction
}

private fun rotate(
    bitmap: Bitmap,
    degrees: Float,
): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    matrix.mapRect(bounds)
    val newWidth = bounds.width().toInt().coerceAtLeast(1)
    val newHeight = bounds.height().toInt().coerceAtLeast(1)
    val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val translated = Matrix(matrix).apply { postTranslate(-bounds.left, -bounds.top) }
    canvas.drawBitmap(bitmap, translated, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return result
}

private fun scaleDownForAnalysis(
    source: Bitmap,
    targetLongEdge: Int,
): Bitmap {
    val longEdge = maxOf(source.width, source.height)
    if (longEdge <= targetLongEdge) return source
    val scale = targetLongEdge.toFloat() / longEdge
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
}
