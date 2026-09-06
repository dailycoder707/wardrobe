package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

private const val SIZE = 10

/**
 * M25 Gemini-segmentation follow-up: [compositeGeminiSegmentationCutout]
 * turns Gemini's own real per-pixel mask (a normalized box + a base64 PNG
 * probability map, both genuine model output) into an actual transparent
 * cutout — these tests prove the RGB values it produces are exactly the
 * source photo's own pixels (never regenerated), that the alpha channel
 * follows the mask/box faithfully, and that malformed input degrades to
 * `null` rather than a fabricated/partial result.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiSegmentationCutoutTest {
    private fun solidBitmap(
        size: Int,
        argb: Int,
    ): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until size) for (x in 0 until size) setPixel(x, y, argb)
        }

    private fun base64Png(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun `RGB values are exactly the source photo's own pixels, never regenerated`() {
        val sourceArgb = Color.argb(255, 10, 20, 30)
        val source = solidBitmap(SIZE, sourceArgb)
        val fullyOpaqueMask = base64Png(solidBitmap(SIZE, Color.argb(255, 255, 255, 255)))

        val result = compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 1000), fullyOpaqueMask)

        requireNotNull(result)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val pixel = result.bitmap.getPixel(x, y)
                assertEquals(10, Color.red(pixel))
                assertEquals(20, Color.green(pixel))
                assertEquals(30, Color.blue(pixel))
            }
        }
    }

    @Test
    fun `a fully-opaque mask covering the whole box makes the whole box opaque, outside the box stays transparent`() {
        val source = solidBitmap(SIZE, Color.argb(255, 1, 2, 3))
        val fullyOpaqueMask = base64Png(solidBitmap(4, Color.argb(255, 255, 255, 255)))
        // Box covers roughly the left half of a 10x10 image: x in [0,500)/1000 -> [0,5).
        val result = compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 500), fullyOpaqueMask)

        requireNotNull(result)
        assertTrue("inside the box should be opaque", Color.alpha(result.bitmap.getPixel(1, 1)) > 0)
        assertEquals("outside the box should be fully transparent", 0, Color.alpha(result.bitmap.getPixel(8, 8)))
    }

    @Test
    fun `a fully-transparent (black) mask leaves the whole box transparent`() {
        val source = solidBitmap(SIZE, Color.argb(255, 5, 6, 7))
        val blackMask = base64Png(solidBitmap(SIZE, Color.argb(255, 0, 0, 0)))

        val result = compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 1000), blackMask)

        requireNotNull(result)
        assertEquals(0, Color.alpha(result.bitmap.getPixel(SIZE / 2, SIZE / 2)))
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `confidence is a real average of the mask's own sampled values, not a placeholder`() {
        val source = solidBitmap(SIZE, Color.argb(255, 1, 1, 1))
        val fullyOpaqueMask = base64Png(solidBitmap(SIZE, Color.argb(255, 255, 255, 255)))

        val result = compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 1000), fullyOpaqueMask)

        requireNotNull(result)
        assertEquals(1f, result.confidence, 0.01f)
    }

    @Test
    fun `returns null when box_2d does not have exactly 4 elements`() {
        val source = solidBitmap(SIZE, Color.BLACK)

        assertNull(compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000), "irrelevant"))
    }

    @Test
    fun `returns null for a degenerate box (max less than or equal to min)`() {
        val source = solidBitmap(SIZE, Color.BLACK)

        assertNull(compositeGeminiSegmentationCutout(source, listOf(500, 500, 500, 500), "irrelevant"))
    }

    /** Mirrors `GenericRestAdapterTest`'s own proven case for the same
     * defensive contract on an untrusted base64 payload: malformed padding
     * is what real `Base64.decode` (and Robolectric's shadow of it) reliably
     * throws on. A well-formed-base64-but-not-actually-a-PNG payload is not
     * separately assertable here — Robolectric's default `BitmapFactory`
     * shadow doesn't validate real image bytes the way the framework does
     * on a real device, so that branch is covered by
     * [decodeMaskBitmap]'s KDoc/production behavior, not a further unit
     * test that would pass for the wrong reason under this shadow. */
    @Test
    fun `returns null for undecodable base64 mask data rather than crashing`() {
        val source = solidBitmap(SIZE, Color.BLACK)

        assertNull(compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 1000), "A"))
    }

    @Test
    fun `a data URI prefix on the mask is stripped before decoding`() {
        val source = solidBitmap(SIZE, Color.argb(255, 9, 9, 9))
        val fullyOpaqueMask = "data:image/png;base64,${base64Png(solidBitmap(SIZE, Color.argb(255, 255, 255, 255)))}"

        val result = compositeGeminiSegmentationCutout(source, listOf(0, 0, 1000, 1000), fullyOpaqueMask)

        requireNotNull(result)
        assertTrue(Color.alpha(result.bitmap.getPixel(SIZE / 2, SIZE / 2)) > 0)
    }
}
