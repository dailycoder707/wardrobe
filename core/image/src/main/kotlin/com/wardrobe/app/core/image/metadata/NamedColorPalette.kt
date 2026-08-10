package com.wardrobe.app.core.image.metadata

import kotlin.math.sqrt

/**
 * A small, fixed set of everyday color names and their representative RGB
 * values — [OnDeviceMetadataEngine] doesn't have a trained color-naming
 * model, so a suggested color name is always "nearest entry in this list by
 * Euclidean RGB distance," never a fine-grained/calibrated color name.
 * Disclosed as approximate — this is this project's own reference palette,
 * not an industry color-naming standard.
 */
internal object NamedColorPalette {
    private data class NamedColor(
        val name: String,
        val r: Int,
        val g: Int,
        val b: Int,
    )

    private val entries =
        listOf(
            NamedColor("Black", 20, 20, 20),
            NamedColor("White", 245, 245, 245),
            NamedColor("Gray", 128, 128, 128),
            NamedColor("Red", 200, 30, 30),
            NamedColor("Orange", 220, 120, 30),
            NamedColor("Yellow", 220, 200, 40),
            NamedColor("Green", 60, 130, 60),
            NamedColor("Teal", 30, 130, 130),
            NamedColor("Blue", 40, 80, 180),
            NamedColor("Navy", 20, 30, 80),
            NamedColor("Purple", 120, 60, 150),
            NamedColor("Pink", 220, 130, 170),
            NamedColor("Brown", 110, 70, 40),
            NamedColor("Beige", 210, 190, 150),
            NamedColor("Cream", 235, 225, 200),
        )

    /** @return the nearest named color and a `0..1` naming confidence — `1`
     * when the sampled color exactly matches a reference entry, decaying
     * toward `0` as it approaches the midpoint distance between the two
     * closest entries. A real, computed distance, not a fabricated number —
     * but the *meaning* of "confidence" here is inherently approximate,
     * since this palette has no claim to being perceptually calibrated. */
    fun nearest(
        r: Int,
        g: Int,
        b: Int,
    ): Pair<String, Float> {
        val distances =
            entries
                .map { entry ->
                    entry.name to distance(r, g, b, entry.r, entry.g, entry.b)
                }.sortedBy { it.second }
        val (nearestName, nearestDistance) = distances.first()
        val secondDistance = distances.getOrNull(1)?.second ?: MAX_RGB_DISTANCE
        val confidence =
            if (secondDistance <= 0f) {
                0f
            } else {
                (1f - nearestDistance / secondDistance).coerceIn(0f, 1f)
            }
        return nearestName to confidence
    }

    private fun distance(
        r1: Int,
        g1: Int,
        b1: Int,
        r2: Int,
        g2: Int,
        b2: Int,
    ): Float {
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private const val MAX_RGB_DISTANCE = 441.67f
}
