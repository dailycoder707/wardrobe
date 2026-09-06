package com.wardrobe.app.core.image.reconstruction

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SIZE = 40

@RunWith(RobolectricTestRunner::class)
class OnDeviceReconstructionEngineTest {
    @Test
    fun `enclosedHoleAreaFraction is zero for a fully opaque bitmap`() {
        val bitmap = filledBitmap(SIZE, SIZE, Color.RED)
        assertEquals(0f, enclosedHoleAreaFraction(bitmap))
    }

    @Test
    fun `enclosedHoleAreaFraction is zero for a normal cutout with transparent background`() {
        // Transparent background with an opaque square in the middle — the classic
        // cutout shape. Every transparent pixel is reachable from the border, so
        // none of it should register as an enclosed hole.
        val bitmap = filledBitmap(SIZE, SIZE, Color.TRANSPARENT)
        fillRect(bitmap, 10, 10, 30, 30, Color.RED)
        assertEquals(0f, enclosedHoleAreaFraction(bitmap))
    }

    @Test
    fun `enclosedHoleAreaFraction is positive when a transparent hole is fully enclosed by opaque pixels`() {
        // Opaque square with a small transparent hole punched in its middle,
        // never touching the square's own edge — that hole cannot be reached
        // from the bitmap's border through other transparent pixels.
        val bitmap = filledBitmap(SIZE, SIZE, Color.RED)
        fillRect(bitmap, 15, 15, 25, 25, Color.TRANSPARENT)
        val severity = enclosedHoleAreaFraction(bitmap)
        assertTrue("expected a positive occlusion severity, got $severity", severity > 0f)
    }

    @Test
    fun `reconstruct always returns NotAttempted, never invents pixels`() =
        runTest {
            val bitmap = filledBitmap(SIZE, SIZE, Color.RED)
            val engine = OnDeviceReconstructionEngine()
            val result = engine.reconstruct(bitmap, extractionConfidence = 0.9f)
            assertTrue(result is ReconstructionResult.NotAttempted)
        }
}

private fun filledBitmap(
    width: Int,
    height: Int,
    color: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    fillRect(bitmap, 0, 0, width, height, color)
    return bitmap
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
