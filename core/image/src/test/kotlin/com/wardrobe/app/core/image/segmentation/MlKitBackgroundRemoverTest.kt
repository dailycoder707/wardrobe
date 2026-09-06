package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.FloatBuffer

private const val SIZE = 4

/**
 * M25 real-device finding: ML Kit's raw per-pixel confidence mask used to be
 * discarded entirely (only ever averaged into a single scalar) — the actual
 * alpha written to the saved cutout came straight from ML Kit's own
 * `foregroundBitmap`, which leaves a moderately-confident pixel (dark
 * fabric, a busy print, a garment close in tone to its background) exactly
 * that transparent even when it's well inside the garment's own silhouette,
 * not near an edge. [hardenAlpha]/[alphaCurve] fix that by remapping the
 * real confidence values onto a hard 0/255 curve outside a genuinely
 * ambiguous middle band.
 */
@RunWith(RobolectricTestRunner::class)
class MlKitBackgroundRemoverTest {
    @Test
    fun `alphaCurve maps confidently-foreground values to fully opaque`() {
        assertEquals(255, alphaCurve(1.0f))
        assertEquals(255, alphaCurve(0.9f))
    }

    @Test
    fun `alphaCurve maps confidently-background values to fully transparent`() {
        assertEquals(0, alphaCurve(0.0f))
        assertEquals(0, alphaCurve(0.1f))
    }

    @Test
    fun `alphaCurve ramps only within the genuinely ambiguous middle band`() {
        val midway = alphaCurve(0.5f)

        assertTrue("midway confidence should be neither fully opaque nor fully transparent", midway in 1..254)
    }

    @Test
    fun `hardenAlpha promotes a confidently-foreground pixel to fully opaque, not left at its raw 217 slash 255`() {
        val foreground = solidBitmap(SIZE, Color.argb(128, 10, 20, 30))
        // A raw-alpha passthrough would leave this pixel at ~217/255 (85%)
        // — visibly not-quite-opaque even though ML Kit is clearly calling
        // it foreground, not near an edge at all.
        val mask = FloatBuffer.wrap(FloatArray(SIZE * SIZE) { 0.85f })

        val result = hardenAlpha(foreground, mask)

        assertEquals(255, Color.alpha(result.getPixel(SIZE / 2, SIZE / 2)))
    }

    @Test
    fun `hardenAlpha never touches RGB values, only alpha`() {
        val foreground = solidBitmap(SIZE, Color.argb(200, 11, 22, 33))
        val mask = FloatBuffer.wrap(FloatArray(SIZE * SIZE) { 1.0f })

        val result = hardenAlpha(foreground, mask)

        val pixel = result.getPixel(0, 0)
        assertEquals(11, Color.red(pixel))
        assertEquals(22, Color.green(pixel))
        assertEquals(33, Color.blue(pixel))
    }

    @Test
    fun `hardenAlpha clears a confidently-background pixel to fully transparent`() {
        val foreground = solidBitmap(SIZE, Color.argb(255, 5, 5, 5))
        val mask = FloatBuffer.wrap(FloatArray(SIZE * SIZE) { 0.0f })

        val result = hardenAlpha(foreground, mask)

        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
    }

    @Test
    fun `hardenAlpha returns the foreground unchanged when the mask size doesn't match the bitmap`() {
        val foreground = solidBitmap(SIZE, Color.argb(255, 1, 2, 3))
        val mismatchedMask = FloatBuffer.wrap(FloatArray(1) { 1.0f })

        val result = hardenAlpha(foreground, mismatchedMask)

        assertEquals(foreground, result)
    }
}

private fun solidBitmap(
    size: Int,
    argb: Int,
): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) setPixel(x, y, argb)
    }
