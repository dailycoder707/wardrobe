package com.wardrobe.app.core.ai.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val PIXELATION_FACTOR = 12

/**
 * Add-to-Wardrobe v2 / ADR-012's pre-upload face redaction — only relevant
 * to the Extraction cloud call, the one capability whose input photo can
 * still contain a face (every other capability's cloud call already sends
 * the post-extraction, faceless garment cutout). On-device only, nothing
 * here ever leaves the device — it exists to reduce what a *later* network
 * call sends, not to be a network operation itself.
 */
interface FaceBlurrer {
    suspend fun blurFaces(bitmap: Bitmap): Bitmap
}

@Singleton
class MlKitFaceBlurrer
    @Inject
    constructor() : FaceBlurrer {
        private val detector by lazy {
            val options =
                FaceDetectorOptions
                    .Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .build()
            FaceDetection.getClient(options)
        }

        override suspend fun blurFaces(bitmap: Bitmap): Bitmap {
            val faces = detectFaces(bitmap)
            return if (faces.isNullOrEmpty()) bitmap else pixelateRegions(bitmap, faces.map(Face::getBoundingBox))
        }

        private suspend fun detectFaces(bitmap: Bitmap): List<Face>? =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                detector
                    .process(input)
                    .addOnSuccessListener { faces -> continuation.resume(faces) }
                    .addOnFailureListener { continuation.resume(null) }
            }
    }

/** Downsample-then-upsample (nearest-neighbor) pixelation — a simple,
 * dependency-free redaction that doesn't rely on any deprecated blur API.
 * Blocky and visible, deliberately: it should be obvious a region was
 * redacted, not a subtle blur someone might mistake for image quality. */
internal fun pixelateRegions(
    source: Bitmap,
    rects: List<Rect>,
): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    rects.forEach { rect -> pixelateRegion(canvas, result, clampToBitmap(rect, result.width, result.height)) }
    return result
}

private fun clampToBitmap(
    rect: Rect,
    width: Int,
    height: Int,
): Rect =
    Rect(
        rect.left.coerceIn(0, width),
        rect.top.coerceIn(0, height),
        rect.right.coerceIn(0, width),
        rect.bottom.coerceIn(0, height),
    )

private fun pixelateRegion(
    canvas: Canvas,
    bitmap: Bitmap,
    rect: Rect,
) {
    val width = rect.width()
    val height = rect.height()
    if (width <= 0 || height <= 0) return
    val region = Bitmap.createBitmap(bitmap, rect.left, rect.top, width, height)
    val smallWidth = (width / PIXELATION_FACTOR).coerceAtLeast(1)
    val smallHeight = (height / PIXELATION_FACTOR).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, true)
    val pixelated = Bitmap.createScaledBitmap(small, width, height, false)
    canvas.drawBitmap(pixelated, rect.left.toFloat(), rect.top.toFloat(), null)
}
