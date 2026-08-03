package com.wardrobe.app.core.tryon.masking

import android.graphics.Bitmap
import android.graphics.Color

private const val DEFAULT_BRUSH_RADIUS_PX = 24f
private const val ALPHA_SHIFT = 24
private const val ALPHA_CLEAR_MASK = 0x00FFFFFF
private const val MIN_ALPHA = 0
private const val MAX_ALPHA = 255

/**
 * Manual erase/restore over a garment cutout's own pixel space — entirely
 * user-drawn, no auto-segmentation ("no AI for this step" per the brief).
 * [erase] zeroes alpha within a brush radius; [restore] sets alpha back to
 * whatever the *original*, never-edited cutout had at that pixel — so
 * restoring can never invent coverage the source image never had, even
 * after several erase/restore passes over the same area.
 */
object GarmentMaskEditor {
    fun erase(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        radiusPx: Float = DEFAULT_BRUSH_RADIUS_PX,
    ): Bitmap = applyBrush(bitmap, x, y, radiusPx) { _, _ -> 0 }

    fun restore(
        bitmap: Bitmap,
        original: Bitmap,
        x: Int,
        y: Int,
        radiusPx: Float = DEFAULT_BRUSH_RADIUS_PX,
    ): Bitmap =
        applyBrush(bitmap, x, y, radiusPx) { px, py ->
            Color.alpha(original.getPixel(px, py))
        }

    private fun applyBrush(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radiusPx: Float,
        alphaAt: (Int, Int) -> Int,
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val radiusSquared = radiusPx * radiusPx
        val minX = (centerX - radiusPx).toInt().coerceAtLeast(0)
        val maxX = (centerX + radiusPx).toInt().coerceAtMost(bitmap.width - 1)
        val minY = (centerY - radiusPx).toInt().coerceAtLeast(0)
        val maxY = (centerY + radiusPx).toInt().coerceAtMost(bitmap.height - 1)
        for (py in minY..maxY) {
            for (px in minX..maxX) {
                val dx = (px - centerX).toFloat()
                val dy = (py - centerY).toFloat()
                if (dx * dx + dy * dy <= radiusSquared) {
                    val pixel = result.getPixel(px, py)
                    val newAlpha = alphaAt(px, py).coerceIn(MIN_ALPHA, MAX_ALPHA)
                    result.setPixel(px, py, (pixel and ALPHA_CLEAR_MASK) or (newAlpha shl ALPHA_SHIFT))
                }
            }
        }
        return result
    }
}
