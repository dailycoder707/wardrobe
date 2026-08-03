package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import com.wardrobe.app.core.model.garment.NormalizedRect

/**
 * Applies a [NormalizedRect] to a decoded bitmap in-memory — there is no
 * persisted "cropped" file (see `ImageFileNames`'s doc comment); the result
 * here feeds straight into background removal / thumbnail generation.
 */
object ImageCropper {
    fun crop(
        bitmap: Bitmap,
        rect: NormalizedRect,
    ): Bitmap {
        val left = (rect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
