package com.wardrobe.app.core.tryon.lighting

import android.graphics.Bitmap
import com.wardrobe.app.core.model.tryon.LightingAdjustment
import kotlin.math.max

/** A reasoned reference mid-tone, not spike-measured against real garment
 * cutouts (this environment has none) — see `phase-10-personal-virtual-
 * tryon.md`'s Known Limitations. Roughly a typical evenly, daylight-lit
 * subject's mean channel value; a body photo brighter/dimmer or with a
 * color cast relative to this shifts the garment layers' color-grade
 * toward matching it. */
private const val REFERENCE_CHANNEL_MEAN = 158f

/** Every 4th pixel in each dimension — a real mean of a representative
 * sample, not the full image, purely for performance; the sampled mean
 * converges to the true mean for any photo that isn't pathologically
 * structured at exactly this stride. */
private const val SAMPLE_STRIDE = 4
private const val CHANNEL_COUNT = 3f

/**
 * Deterministic, non-ML — one histogram pass over the active body
 * profile's [com.wardrobe.app.core.model.tryon.BodyPose.NEUTRAL] photo,
 * producing a real, measured [LightingAdjustment] rather than a fabricated
 * one. A color-grade heuristic, not true relighting (see
 * [LightingAdjustment]'s KDoc) — it only shifts garment layers' brightness/
 * color balance toward matching the body photo's own, it cannot correct for
 * directional shadows the garment cutout wasn't originally lit by.
 */
object LightingMatcher {
    fun match(bitmap: Bitmap): LightingAdjustment {
        val (meanR, meanG, meanB) = channelMeans(bitmap)
        val meanLuminance = (meanR + meanG + meanB) / CHANNEL_COUNT
        return LightingAdjustment(
            brightnessDelta = meanLuminance - REFERENCE_CHANNEL_MEAN,
            contrastFactor = 1f,
            colorGainR = meanR / REFERENCE_CHANNEL_MEAN,
            colorGainG = meanG / REFERENCE_CHANNEL_MEAN,
            colorGainB = meanB / REFERENCE_CHANNEL_MEAN,
        )
    }

    private fun channelMeans(bitmap: Bitmap): Triple<Float, Float, Float> {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                sumR += (pixel shr RED_SHIFT) and CHANNEL_MASK
                sumG += (pixel shr GREEN_SHIFT) and CHANNEL_MASK
                sumB += pixel and CHANNEL_MASK
                count++
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }
        val safeCount = max(count, 1L).toFloat()
        return Triple(sumR / safeCount, sumG / safeCount, sumB / safeCount)
    }
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
