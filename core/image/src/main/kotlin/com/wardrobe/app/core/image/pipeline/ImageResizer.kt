package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.os.Build
import java.io.OutputStream
import kotlin.math.max

/**
 * Resize/compress per ADR-007's encoding decisions. `WEBP_LOSSLESS`/
 * `WEBP_LOSSY` were added in API 30; below that (down to this project's
 * minSdk 26) the legacy `WEBP` format is used instead — it's always lossy on
 * those OS versions (there is no lossless control pre-30), a documented,
 * deliberate compatibility fallback rather than an oversight. The quality
 * difference is negligible at this app's 2048px cap for a product-style photo.
 */
object ImageResizer {
    fun resizeToLongEdge(
        bitmap: Bitmap,
        maxLongEdge: Int,
    ): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun encodeJpeg(
        bitmap: Bitmap,
        quality: Int,
        out: OutputStream,
    ) {
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
    }

    fun encodeWebpLossless(
        bitmap: Bitmap,
        out: OutputStream,
    ) {
        val format =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        val quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) LOSSLESS_QUALITY else LEGACY_WEBP_QUALITY
        check(bitmap.compress(format, quality, out)) { "WebP (lossless) encode failed" }
    }

    fun encodeWebpLossy(
        bitmap: Bitmap,
        quality: Int,
        out: OutputStream,
    ) {
        val format =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        check(bitmap.compress(format, quality, out)) { "WebP (lossy) encode failed" }
    }

    private const val LOSSLESS_QUALITY = 100
    private const val LEGACY_WEBP_QUALITY = 95
}
