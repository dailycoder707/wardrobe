package com.wardrobe.app.core.image.presentation

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

private const val CANVAS_SIZE = 60
private const val SQUARE_START = 20
private const val SQUARE_END = 40

@RunWith(RobolectricTestRunner::class)
class GarmentPresentationEnhancerTest {
    private val enhancer = DefaultGarmentPresentationEnhancer()

    @Test
    fun `enhance crops down to roughly the opaque bounds, discarding most transparent padding`() {
        val padded = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        fillRect(padded, SQUARE_START, SQUARE_START, SQUARE_END, SQUARE_END, Color.RED)

        val result = enhancer.enhance(padded)

        assertTrue(
            "expected the cropped cutout to be substantially smaller than the padded source",
            result.enhancedCutout.width < CANVAS_SIZE && result.enhancedCutout.height < CANVAS_SIZE,
        )
    }

    @Test
    fun `enhance's white background variant has pure white corners`() {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        fillRect(bitmap, SQUARE_START, SQUARE_START, SQUARE_END, SQUARE_END, Color.BLUE)

        val result = enhancer.enhance(bitmap)
        val whiteBg = result.whiteBackgroundVariant

        assertEquals(Color.WHITE, whiteBg.getPixel(0, 0))
        assertEquals(Color.WHITE, whiteBg.getPixel(whiteBg.width - 1, whiteBg.height - 1))
    }

    @Test
    fun `enhance's white balance narrows a color cast between channels`() {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        // A strong red cast (a garment that's actually gray but the photo has a
        // warm color cast) — gray-world balancing should narrow the gap between
        // the red channel and the others.
        fillRect(bitmap, SQUARE_START, SQUARE_START, SQUARE_END, SQUARE_END, Color.rgb(220, 120, 120))

        val before = channelMeans(bitmap, SQUARE_START, SQUARE_END)
        val result = enhancer.enhance(bitmap)
        val after = channelMeans(result.enhancedCutout, 0, result.enhancedCutout.width)

        val gapBefore = abs(before.first - before.second)
        val gapAfter = abs(after.first - after.second)
        assertTrue(
            "expected the R/G channel gap to narrow after white balance (before=$gapBefore, after=$gapAfter)",
            gapAfter < gapBefore,
        )
    }
}

private fun channelMeans(
    bitmap: Bitmap,
    start: Int,
    end: Int,
): Pair<Double, Double> {
    var sumR = 0L
    var sumG = 0L
    var count = 0
    for (y in start until end) {
        for (x in start until end) {
            if (x >= bitmap.width || y >= bitmap.height) continue
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) == 0) continue
            sumR += Color.red(pixel)
            sumG += Color.green(pixel)
            count++
        }
    }
    return if (count == 0) 0.0 to 0.0 else (sumR.toDouble() / count) to (sumG.toDouble() / count)
}

private fun fillRect(
    bitmap: Bitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    color: Int,
) {
    for (y in top until bottom) {
        for (x in left until right) {
            bitmap.setPixel(x, y, color)
        }
    }
}
