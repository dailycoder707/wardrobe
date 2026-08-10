package com.wardrobe.app.core.image.reconstruction

import android.graphics.Bitmap
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** Below this alpha value (0..255) a pixel is treated as transparent — a
 * small tolerance for anti-aliased cutout edges, not a hard cutoff at 0. */
private const val ALPHA_OPAQUE_THRESHOLD = 32
private const val ALPHA_SHIFT_BITS = 24

/** The flood fill runs on a downscaled copy — the severity is a ratio, so
 * exact pixel geometry doesn't matter, and analyzing a full ~2048px cutout
 * pixel-by-pixel would eat into the 3–8s pipeline budget for no accuracy
 * benefit. */
private const val ANALYSIS_LONG_EDGE_PX = 384

/**
 * No on-device inpainting model exists in this stack, so this always
 * returns [ReconstructionResult.NotAttempted] — reconstruction is only ever
 * attempted by a configured cloud provider. What this *does* do honestly:
 * estimate how occluded the garment looks, via
 * [enclosedHoleAreaFraction] — a real, disclosed-as-approximate proxy (not
 * a trained occlusion classifier), so the review screen can prompt a retake
 * on a badly-occluded photo instead of silently saving a hole-riddled
 * cutout.
 */
@Singleton
class OnDeviceReconstructionEngine
    @Inject
    constructor() : GarmentReconstructionEngine {
        override suspend fun reconstruct(
            cutout: Bitmap,
            extractionConfidence: Float?,
        ): ReconstructionResult =
            ReconstructionResult.NotAttempted(
                occlusionSeverity = enclosedHoleAreaFraction(cutout),
                reason = "no_on_device_inpainting_model",
            )
    }

private class PixelGrid(
    bitmap: Bitmap,
) {
    val width = bitmap.width
    val height = bitmap.height
    private val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }

    fun isTransparent(index: Int): Boolean = (pixels[index] ushr ALPHA_SHIFT_BITS) < ALPHA_OPAQUE_THRESHOLD
}

/**
 * Flood-fills from every border pixel through transparent pixels to find
 * "background" — any transparent pixel *not* reached that way is enclosed
 * within the opaque silhouette (the classic signature of an arm crossing in
 * front of a garment, leaving a hole mid-shape). Returns
 * `enclosed transparent area / (opaque area + enclosed transparent area)`,
 * i.e. what fraction of the garment's own silhouette is a hole.
 *
 * A deliberate proxy, not a true occlusion classifier — a garment with a
 * genuine cutout design (a keyhole neckline) also registers non-zero
 * severity here. Disclosed as an honest approximation (Constitution rule
 * 4), not asserted as precise occlusion detection.
 */
internal fun enclosedHoleAreaFraction(source: Bitmap): Float {
    val grid = PixelGrid(scaleForAnalysis(source))
    if (grid.width == 0 || grid.height == 0) return 0f
    val reachableFromBorder = floodFillReachableFromBorder(grid)
    return silhouetteHoleFraction(grid, reachableFromBorder)
}

private fun floodFillReachableFromBorder(grid: PixelGrid): BooleanArray {
    val width = grid.width
    val height = grid.height
    val reachable = BooleanArray(width * height)
    val queue = ArrayDeque<Int>()

    fun enqueue(index: Int) {
        if (grid.isTransparent(index) && !reachable[index]) {
            reachable[index] = true
            queue.add(index)
        }
    }

    for (x in 0 until width) {
        enqueue(x)
        enqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
        enqueue(y * width)
        enqueue(y * width + width - 1)
    }

    while (queue.isNotEmpty()) {
        val index = queue.poll() ?: break
        val x = index % width
        val y = index / width
        if (x > 0) enqueue(index - 1)
        if (x < width - 1) enqueue(index + 1)
        if (y > 0) enqueue(index - width)
        if (y < height - 1) enqueue(index + width)
    }
    return reachable
}

private fun silhouetteHoleFraction(
    grid: PixelGrid,
    reachableFromBorder: BooleanArray,
): Float {
    var opaqueCount = 0
    var enclosedTransparentCount = 0
    for (index in reachableFromBorder.indices) {
        when {
            !grid.isTransparent(index) -> opaqueCount++
            !reachableFromBorder[index] -> enclosedTransparentCount++
        }
    }
    val silhouetteArea = opaqueCount + enclosedTransparentCount
    return if (silhouetteArea == 0) 0f else (enclosedTransparentCount.toFloat() / silhouetteArea).coerceIn(0f, 1f)
}

private fun scaleForAnalysis(source: Bitmap): Bitmap {
    val longEdge = maxOf(source.width, source.height)
    if (longEdge <= ANALYSIS_LONG_EDGE_PX) return source
    val scale = ANALYSIS_LONG_EDGE_PX.toFloat() / longEdge
    val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
}
