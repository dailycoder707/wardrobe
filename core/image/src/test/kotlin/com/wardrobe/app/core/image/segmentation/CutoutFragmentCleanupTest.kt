package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SIZE = 10
private const val OPAQUE = -0x1000000 or 0x000000 // black, alpha 255
private const val TRANSPARENT = 0

/**
 * M25 real-device finding: nothing in the on-device extraction pipeline
 * ever removed a disconnected fragment (a severed sleeve tip, a stray
 * segmentation artifact) from a garment cutout — see
 * [keepLargestOpaqueComponent]'s own KDoc for the full root-cause trace.
 * These tests build small synthetic alpha patterns with a known largest
 * region and known smaller fragments, and prove only the largest survives.
 */
@RunWith(RobolectricTestRunner::class)
class CutoutFragmentCleanupTest {
    private fun bitmapOf(opaqueAt: (Int, Int) -> Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                bitmap.setPixel(x, y, if (opaqueAt(x, y)) OPAQUE else TRANSPARENT)
            }
        }
        return bitmap
    }

    private fun isOpaqueAt(
        bitmap: Bitmap,
        x: Int,
        y: Int,
    ): Boolean = Color.alpha(bitmap.getPixel(x, y)) > 0

    @Test
    fun `a single connected region is left completely untouched`() {
        val bitmap = bitmapOf { x, y -> x in 2..7 && y in 2..7 }

        val cleaned = keepLargestOpaqueComponent(bitmap)

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                assertEquals("mismatch at ($x,$y)", isOpaqueAt(bitmap, x, y), isOpaqueAt(cleaned, x, y))
            }
        }
    }

    @Test
    fun `a small disconnected fragment is cleared, the large main body survives`() {
        // A 4x4 main body plus a single isolated 1x1 fragment far away.
        val bitmap =
            bitmapOf { x, y ->
                (x in 1..4 && y in 1..4) || (x == 8 && y == 8)
            }

        val cleaned = keepLargestOpaqueComponent(bitmap)

        for (y in 1..4) {
            for (x in 1..4) {
                assertEquals("main body pixel ($x,$y) should survive", true, isOpaqueAt(cleaned, x, y))
            }
        }
        assertEquals("the isolated fragment must be cleared", false, isOpaqueAt(cleaned, 8, 8))
    }

    @Test
    fun `a fully transparent bitmap is returned unchanged, never crashes on no opaque pixels`() {
        val bitmap = bitmapOf { _, _ -> false }

        val cleaned = keepLargestOpaqueComponent(bitmap)

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                assertEquals(false, isOpaqueAt(cleaned, x, y))
            }
        }
    }

    @Test
    fun `two regions connected by a thin bridge are treated as one component, both survive`() {
        // A small blob, a single-pixel-wide bridge, and another small blob —
        // 4-connectivity through the bridge makes this one region, so
        // neither half should be cleared even though each alone is small.
        val bitmap =
            bitmapOf { x, y ->
                (x in 0..1 && y in 0..1) || (x == 2 && y == 0) || (x in 3..4 && y in 0..1)
            }

        val cleaned = keepLargestOpaqueComponent(bitmap)

        assertEquals(true, isOpaqueAt(cleaned, 0, 0))
        assertEquals(true, isOpaqueAt(cleaned, 4, 1))
    }
}
