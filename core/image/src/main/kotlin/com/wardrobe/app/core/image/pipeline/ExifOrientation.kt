package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
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
     * [ExifInterface.ORIENTATION_NORMAL] rather than failing the pipeline.
     *
     * [ExifInterface.ORIENTATION_UNDEFINED] is normalized to
     * [ExifInterface.ORIENTATION_NORMAL] for the same reason, and is a real
     * case rather than a theoretical one: androidx's parser reports a tag it
     * cannot make sense of as `UNDEFINED` (0) instead of returning the
     * requested default, so without this the documented "falls back to
     * ORIENTATION_NORMAL" guarantee above would simply not be true. Both
     * values mean "nothing to rotate", and [correct] already treats them
     * identically — this keeps what [readOrientation] itself returns honest
     * for any caller that reads it directly. */
    fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            .takeUnless { it == ExifInterface.ORIENTATION_UNDEFINED }
            ?: ExifInterface.ORIENTATION_NORMAL

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
