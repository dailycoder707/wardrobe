package com.wardrobe.app.core.image.metadata

import android.graphics.Bitmap

private const val MAX_COLOR_SAMPLES = 4000
private const val KMEANS_ITERATIONS = 10

internal data class RgbCentroid(
    val r: Int,
    val g: Int,
    val b: Int,
    val weight: Float,
)

/** Fixed-iteration k=2 k-means in RGB space over a subsampled set of opaque
 * pixels. Deterministic seeding (min/max luminance samples), not random, so
 * results are reproducible. @return `primary to secondary` — `secondary` is
 * `null` if fewer than two distinct opaque samples exist. */
internal fun kMeansColorClusters(bitmap: Bitmap): Pair<RgbCentroid, RgbCentroid?>? {
    val samples = sampleOpaquePixels(bitmap)
    val seeded = seedCentroids(samples) ?: return singleCentroidOrNull(samples)

    var centroid0 = seeded.first
    var centroid1 = seeded.second
    repeat(KMEANS_ITERATIONS) {
        val assignments = assignToNearest(samples, centroid0, centroid1)
        centroid0 = meanOf(samples, assignments, 0) ?: centroid0
        centroid1 = meanOf(samples, assignments, 1) ?: centroid1
    }

    val finalAssignments = assignToNearest(samples, centroid0, centroid1)
    val weight0 = finalAssignments.count { it == 0 }.toFloat() / samples.size
    val weight1 = 1f - weight0
    val (primarySample, primaryWeight) = if (weight0 >= weight1) centroid0 to weight0 else centroid1 to weight1
    val (secondarySample, secondaryWeight) = if (weight0 >= weight1) centroid1 to weight1 else centroid0 to weight0
    return RgbCentroid(primarySample.r, primarySample.g, primarySample.b, primaryWeight) to
        RgbCentroid(secondarySample.r, secondarySample.g, secondarySample.b, secondaryWeight)
}

private fun singleCentroidOrNull(samples: List<RgbSample>): Pair<RgbCentroid, RgbCentroid?>? =
    samples.firstOrNull()?.let { RgbCentroid(it.r, it.g, it.b, 1f) to null }

/** `null` when fewer than two distinct-luminance samples exist — k-means
 * needs two different starting points to converge on two clusters. */
private fun seedCentroids(samples: List<RgbSample>): Pair<RgbSample, RgbSample>? {
    if (samples.size < 2) return null
    val centroid0 = samples.minBy { luminance(it) }
    val centroid1 = samples.maxBy { luminance(it) }
    return if (centroid0 == centroid1) null else centroid0 to centroid1
}

private fun assignToNearest(
    samples: List<RgbSample>,
    centroid0: RgbSample,
    centroid1: RgbSample,
): List<Int> = samples.map { sample -> if (distance(sample, centroid0) <= distance(sample, centroid1)) 0 else 1 }

private fun distance(
    sample: RgbSample,
    centroid: RgbSample,
): Double {
    val dr = (sample.r - centroid.r).toDouble()
    val dg = (sample.g - centroid.g).toDouble()
    val db = (sample.b - centroid.b).toDouble()
    return dr * dr + dg * dg + db * db
}

private fun meanOf(
    samples: List<RgbSample>,
    assignments: List<Int>,
    cluster: Int,
): RgbSample? {
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    var count = 0
    samples.forEachIndexed { index, sample ->
        if (assignments[index] == cluster) {
            sumR += sample.r
            sumG += sample.g
            sumB += sample.b
            count++
        }
    }
    return if (count == 0) null else RgbSample((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
}

private fun sampleOpaquePixels(bitmap: Bitmap): List<RgbSample> {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return emptyList()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val opaqueIndices = pixels.indices.filter { isOpaque(pixels[it]) }
    val stride = (opaqueIndices.size / MAX_COLOR_SAMPLES).coerceAtLeast(1)
    return opaqueIndices
        .filterIndexed { i, _ -> i % stride == 0 }
        .map { index -> decomposeRgb(pixels[index]) }
}
