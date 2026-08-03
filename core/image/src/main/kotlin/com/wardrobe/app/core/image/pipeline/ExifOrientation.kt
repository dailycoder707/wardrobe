package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Reads and applies EXIF orientation, then the corrected bitmap is written back
 * out with no EXIF block at all — `original.jpg` is always stored
 * already-upright, so nothing downstream (thumbnail generation, display) needs
 * to re-read orientation metadata, and no GPS/device metadata a camera may have
 * embedded is ever persisted (Phase 1 Section 26: no PII in anything this app
 * keeps around longer than necessary).
 */
object ExifOrientation {
    /** A file with no EXIF segment at all (common for images produced by
     * in-app processing rather than a camera) is not corrupted — it simply
     * has nothing to correct, so any failure reading it falls back to
     * [ExifInterface.ORIENTATION_NORMAL] rather than failing the pipeline. */
    fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun correct(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(ROTATE_90)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(ROTATE_180)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(ROTATE_270)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(ROTATE_90)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(ROTATE_270)
                matrix.postScale(-1f, 1f)
            }

            else -> {
                return bitmap
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private const val ROTATE_90 = 90f
    private const val ROTATE_180 = 180f
    private const val ROTATE_270 = 270f
}
