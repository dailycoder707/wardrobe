package com.wardrobe.app.core.image.presentation

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    /** M25 real-device finding: this used to assert the opposite — that
     * gray-world balancing narrowed the R/G gap. That was the bug: gray-world
     * over an isolated garment cutout reads the garment's own real color as
     * a "cast" and pulls it toward gray on every save. A real red garment
     * must stay recognizably red, not measurably drift toward the other
     * channels' level, however mild the drift. */
    @Test
    fun `enhance preserves a real garment color's own channel gap, never narrows it toward gray`() {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        fillRect(bitmap, SQUARE_START, SQUARE_START, SQUARE_END, SQUARE_END, Color.rgb(220, 120, 120))
        val gapBefore = 220 - 120

        val result = enhancer.enhance(bitmap)

        // Sampled from the interior, not the crop/deskew-affected boundary —
        // deep inside a uniformly-colored square, resampling from identical
        // neighbors can't change the color, so this isolates the color
        // transform itself from unrelated geometric edge effects.
        val cutout = result.enhancedCutout
        val pixel = cutout.getPixel(cutout.width / 2, cutout.height / 2)
        val gapAfter = Color.red(pixel) - Color.green(pixel)
        assertTrue(
            "expected the garment's own R/G gap to survive enhancement, not narrow toward gray " +
                "(before=$gapBefore, after=$gapAfter)",
            gapAfter >= gapBefore - CONTRAST_ROUNDING_TOLERANCE,
        )
    }

    @Test
    fun `enhance never clips a bright, saturated garment color to white`() {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        // A bright, saturated color a naive gain-then-pivot bug would clip.
        fillRect(bitmap, SQUARE_START, SQUARE_START, SQUARE_END, SQUARE_END, Color.rgb(250, 40, 40))

        val result = enhancer.enhance(bitmap)

        val pixel = result.enhancedCutout.getPixel(result.enhancedCutout.width / 2, result.enhancedCutout.height / 2)
        assertTrue("expected green to stay low, not clip toward white", Color.green(pixel) < 80)
        assertTrue("expected blue to stay low, not clip toward white", Color.blue(pixel) < 80)
    }
}

private const val CONTRAST_ROUNDING_TOLERANCE = 1.0

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
