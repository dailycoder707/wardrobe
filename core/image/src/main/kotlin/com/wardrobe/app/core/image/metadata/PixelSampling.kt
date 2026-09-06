package com.wardrobe.app.core.image.metadata

private const val ALPHA_SHIFT_BITS = 24
private const val RED_SHIFT_BITS = 16
private const val GREEN_SHIFT_BITS = 8
private const val BYTE_MASK = 0xFF
internal const val ALPHA_OPAQUE_THRESHOLD = 32
private const val LUMINANCE_RED_WEIGHT = 3.0
private const val LUMINANCE_GREEN_WEIGHT = 6.0
private const val LUMINANCE_BLUE_WEIGHT = 1.0
private const val LUMINANCE_WEIGHT_DIVISOR = 10.0

/** One decoded ARGB pixel's color channels — shared by
 * [OnDeviceMetadataEngine]'s k-means color clustering and its solid/pattern
 * luminance-variance heuristic, so the bit-shift decoding logic exists in
 * exactly one place. */
internal data class RgbSample(
    val r: Int,
    val g: Int,
    val b: Int,
)

internal fun decomposeRgb(pixel: Int): RgbSample =
    RgbSample(
        r = (pixel shr RED_SHIFT_BITS) and BYTE_MASK,
        g = (pixel shr GREEN_SHIFT_BITS) and BYTE_MASK,
        b = pixel and BYTE_MASK,
    )

internal fun isOpaque(pixel: Int): Boolean = (pixel ushr ALPHA_SHIFT_BITS) >= ALPHA_OPAQUE_THRESHOLD

/** A simplified integer-weighted luma approximation (not exact ITU-R
 * BT.601/709 coefficients) — good enough to rank samples by lightness for
 * k-means seeding and to measure texture variance, not presented as a
 * calibrated luminance measurement. */
internal fun luminance(sample: RgbSample): Double =
    (sample.r * LUMINANCE_RED_WEIGHT + sample.g * LUMINANCE_GREEN_WEIGHT + sample.b * LUMINANCE_BLUE_WEIGHT) /
        LUMINANCE_WEIGHT_DIVISOR
