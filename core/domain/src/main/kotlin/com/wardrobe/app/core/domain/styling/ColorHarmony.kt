package com.wardrobe.app.core.domain.styling

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A lightweight hue-distance heuristic, not a color-theory engine — good
 * enough to give the Outfit Builder (Phase 5d) a calm, descriptive label, and
 * the styling engine (Phase 6) a real color-harmony scoring signal, without
 * pretending to more precision than a few RGB hex values support. Promoted
 * here from `feature:outfits` in Phase 6 so both can call it — this module has
 * zero Android dependencies, same as `core:model`. */
enum class ColorHarmony(
    val label: String,
) {
    NEUTRAL("Neutral"),
    MONOCHROME("Monochrome"),
    ANALOGOUS("Analogous"),
    COMPLEMENTARY("Complementary"),
    MIXED("Mixed"),
}

private const val ACHROMATIC_SATURATION_THRESHOLD = 0.15f
private const val MONOCHROME_SPREAD_DEGREES = 20f
private const val ANALOGOUS_SPREAD_DEGREES = 60f
private const val COMPLEMENTARY_MIN_DEGREES = 150f
private const val COMPLEMENTARY_MAX_DEGREES = 210f
private const val SINGLE_CHROMATIC_HUE_COUNT = 1
private const val HUE_WRAP_DEGREES = 360f

/** `hexValues` are the primary colors of every garment currently in the
 * outfit (one per slot). Returns `null` when there's nothing to analyze yet
 * (an empty outfit). */
fun analyzeColorHarmony(hexValues: List<String>): ColorHarmony? {
    val hslValues = hexValues.mapNotNull { parseHexToHsl(it) }
    val chromaticHues = hslValues.filter { it.saturation >= ACHROMATIC_SATURATION_THRESHOLD }.map { it.hue }
    val spread = hueSpread(chromaticHues)

    return when {
        hexValues.isEmpty() || hslValues.isEmpty() -> null
        chromaticHues.isEmpty() -> ColorHarmony.NEUTRAL
        chromaticHues.size == SINGLE_CHROMATIC_HUE_COUNT -> ColorHarmony.MONOCHROME
        spread <= MONOCHROME_SPREAD_DEGREES -> ColorHarmony.MONOCHROME
        spread in COMPLEMENTARY_MIN_DEGREES..COMPLEMENTARY_MAX_DEGREES -> ColorHarmony.COMPLEMENTARY
        spread <= ANALOGOUS_SPREAD_DEGREES -> ColorHarmony.ANALOGOUS
        else -> ColorHarmony.MIXED
    }
}

/** The largest angular distance between any two hues, on a 0..360 wheel
 * (so two hues near 350° and 10° are correctly seen as 20° apart, not 340°). */
private fun hueSpread(hues: List<Float>): Float {
    var maxSpread = 0f
    for (i in hues.indices) {
        for (j in i + 1 until hues.size) {
            val diff = abs(hues[i] - hues[j])
            val angularDiff = min(diff, HUE_WRAP_DEGREES - diff)
            maxSpread = max(maxSpread, angularDiff)
        }
    }
    return maxSpread
}

private data class Hsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

private data class Rgb(
    val r: Float,
    val g: Float,
    val b: Float,
)

private const val HEX_COLOR_LENGTH = 6
private const val RGB_MAX = 255f
private const val HUE_SEGMENT_DEGREES = 60f
private const val HUE_MODULO = 6f
private const val GREEN_HUE_OFFSET = 2f
private const val BLUE_HUE_OFFSET = 4f
private const val HEX_RADIX = 16
private const val RED_CHANNEL_START = 0
private const val RED_CHANNEL_END = 2
private const val GREEN_CHANNEL_END = 4
private const val BLUE_CHANNEL_END = 6

private fun parseHexToHsl(hex: String): Hsl? {
    val rgb = parseRgbChannels(hex.removePrefix("#")) ?: return null
    val maxChannel = max(rgb.r, max(rgb.g, rgb.b))
    val minChannel = min(rgb.r, min(rgb.g, rgb.b))
    val lightness = (maxChannel + minChannel) / 2f
    val delta = maxChannel - minChannel

    return if (delta == 0f) {
        Hsl(hue = 0f, saturation = 0f, lightness = lightness)
    } else {
        val saturation = delta / (1f - abs(2f * lightness - 1f))
        Hsl(hue = hueDegrees(rgb, maxChannel, delta), saturation = saturation, lightness = lightness)
    }
}

private fun parseRgbChannels(cleaned: String): Rgb? {
    if (cleaned.length != HEX_COLOR_LENGTH) return null
    val r = cleaned.substring(RED_CHANNEL_START, RED_CHANNEL_END).toIntOrNull(HEX_RADIX)
    val g = cleaned.substring(RED_CHANNEL_END, GREEN_CHANNEL_END).toIntOrNull(HEX_RADIX)
    val b = cleaned.substring(GREEN_CHANNEL_END, BLUE_CHANNEL_END).toIntOrNull(HEX_RADIX)
    return if (r == null || g == null || b == null) null else Rgb(r / RGB_MAX, g / RGB_MAX, b / RGB_MAX)
}

private fun hueDegrees(
    rgb: Rgb,
    maxChannel: Float,
    delta: Float,
): Float {
    val hue =
        when (maxChannel) {
            rgb.r -> HUE_SEGMENT_DEGREES * (((rgb.g - rgb.b) / delta) % HUE_MODULO)
            rgb.g -> HUE_SEGMENT_DEGREES * (((rgb.b - rgb.r) / delta) + GREEN_HUE_OFFSET)
            else -> HUE_SEGMENT_DEGREES * (((rgb.r - rgb.g) / delta) + BLUE_HUE_OFFSET)
        }
    return if (hue < 0) hue + HUE_WRAP_DEGREES else hue
}
