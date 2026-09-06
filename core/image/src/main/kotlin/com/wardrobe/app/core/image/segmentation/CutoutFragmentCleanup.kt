package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap

/** Same soft opaque/transparent boundary `GarmentPresentationEnhancer` uses
 * for its own bounds crop — a pixel counts as "part of the garment" once
 * its alpha crosses this, not only at a hard 255. */
private const val ALPHA_OPAQUE_THRESHOLD = 32
private const val ALPHA_SHIFT = 24
private const val ALPHA_MASK = 0xFF
private const val RGB_MASK = 0x00FFFFFF

/**
 * M25 real-device finding: neither the raw ML Kit cutout nor
 * [MlKitPersonRegionMasker]'s pose-landmark punch-outs are followed by any
 * cleanup that removes disconnected fragments — a severed sleeve tip, a
 * stray hair strand the segmenter treated as foreground, or a punch-out
 * that happens to bisect a loose sleeve all survive all the way to the
 * saved cutout today. `GarmentPresentationEnhancer`'s own bounds-crop
 * (`ALPHA_OPAQUE_THRESHOLD`-based) computes a *bounding rectangle* over
 * every opaque pixel including fragments — it trims empty margins, it does
 * not discard a stray blob sitting inside those margins.
 *
 * This keeps only the single largest 4-connected opaque region and clears
 * every other pixel to fully transparent — a real, deterministic,
 * O(width×height) fix for the *fragment* class of artifact specifically
 * (detached blobs), not a fix for mask quality within the main garment
 * silhouette itself (that traces to ML Kit's own closed-source segmenter
 * or to a punch-out sized too aggressively — see [MlKitPersonRegionMasker]'s
 * own disclosed limitations). A garment that's genuinely split into two
 * large, actually-disconnected pieces in one photo (unlikely for this
 * app's one-garment-per-photo capture flow) would lose the smaller piece
 * here — an accepted, disclosed tradeoff for reliably removing noise
 * fragments, not a scenario this capture flow is designed around.
 *
 * Public (not `internal`) since M25's Gemini cloud segmentation path
 * (`compositeGeminiSegmentationCutout`) reuses this same defensive cleanup
 * on its own composited cutout — a real per-object mask from Gemini should
 * already be one coherent region, but nothing about that is guaranteed by
 * the provider, so the same fragment-removal guarantee applies to both the
 * on-device and cloud extraction paths rather than only one of them.
 */
fun keepLargestOpaqueComponent(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return bitmap
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val largest = largestComponent(pixels, width, height)
    return if (largest == null) {
        bitmap
    } else {
        for (index in pixels.indices) {
            if (largest.labels[index] != largest.label) pixels[index] = pixels[index] and RGB_MASK
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}

private class LargestComponent(
    val labels: IntArray,
    val label: Int,
)

/** `null` when the cutout has no opaque pixels at all — nothing to keep,
 * so the caller returns the (fully transparent) source unchanged. */
private fun largestComponent(
    pixels: IntArray,
    width: Int,
    height: Int,
): LargestComponent? {
    val labels = IntArray(pixels.size)
    var largestLabel = 0
    var largestSize = 0
    var nextLabel = 1
    for (start in pixels.indices) {
        if (labels[start] != 0 || !pixels[start].isOpaque()) continue
        val size = floodFill(pixels, labels, start, width, height, nextLabel)
        if (size > largestSize) {
            largestSize = size
            largestLabel = nextLabel
        }
        nextLabel++
    }
    return if (largestLabel == 0) null else LargestComponent(labels, largestLabel)
}

private fun Int.isOpaque(): Boolean = (this ushr ALPHA_SHIFT and ALPHA_MASK) >= ALPHA_OPAQUE_THRESHOLD

/** Iterative 4-connected flood fill (an explicit stack, not recursion —
 * a garment cutout's main region can easily exceed the JVM's default call
 * stack depth for a recursive per-pixel visit). Returns the region's pixel
 * count so the caller can track the largest without a second pass. */
private fun floodFill(
    pixels: IntArray,
    labels: IntArray,
    start: Int,
    width: Int,
    height: Int,
    label: Int,
): Int {
    val stack = ArrayDeque<Int>()
    stack.addLast(start)
    labels[start] = label
    var size = 0
    while (stack.isNotEmpty()) {
        val index = stack.removeLast()
        size++
        val x = index % width
        val y = index / width
        if (x > 0) visit(pixels, labels, index - 1, label, stack)
        if (x < width - 1) visit(pixels, labels, index + 1, label, stack)
        if (y > 0) visit(pixels, labels, index - width, label, stack)
        if (y < height - 1) visit(pixels, labels, index + width, label, stack)
    }
    return size
}

private fun visit(
    pixels: IntArray,
    labels: IntArray,
    index: Int,
    label: Int,
    stack: ArrayDeque<Int>,
) {
    if (labels[index] == 0 && pixels[index].isOpaque()) {
        labels[index] = label
        stack.addLast(index)
    }
}
