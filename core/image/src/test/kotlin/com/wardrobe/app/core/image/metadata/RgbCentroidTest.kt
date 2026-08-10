package com.wardrobe.app.core.image.metadata

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SIZE = 20

@RunWith(RobolectricTestRunner::class)
class RgbCentroidTest {
    @Test
    fun `kMeansColorClusters finds two clusters near red and blue for a half-red half-blue bitmap`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                bitmap.setPixel(x, y, if (x < SIZE / 2) Color.RED else Color.BLUE)
            }
        }

        val clusters = kMeansColorClusters(bitmap)
        assertNotNull(clusters)
        val (primary, secondary) = clusters!!
        assertNotNull(secondary)

        val isRedLike = { c: RgbCentroid -> c.r > 150 && c.b < 100 }
        val isBlueLike = { c: RgbCentroid -> c.b > 150 && c.r < 100 }
        assertTrue(
            "expected one cluster near red and one near blue, got $primary / $secondary",
            (isRedLike(primary) && isBlueLike(secondary!!)) || (isBlueLike(primary) && isRedLike(secondary!!)),
        )
        assertTrue("expected roughly balanced 50/50 weights, got ${primary.weight}", primary.weight in 0.4f..0.6f)
    }

    @Test
    fun `kMeansColorClusters reports no secondary color for a uniformly-colored bitmap`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                bitmap.setPixel(x, y, Color.GREEN)
            }
        }

        val clusters = kMeansColorClusters(bitmap)
        assertNotNull(clusters)
        assertNull(clusters!!.second)
    }

    @Test
    fun `kMeansColorClusters ignores transparent background pixels`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val insideSquare = x in 5 until 15 && y in 5 until 15
                bitmap.setPixel(x, y, if (insideSquare) Color.MAGENTA else Color.TRANSPARENT)
            }
        }

        val clusters = kMeansColorClusters(bitmap)
        assertNotNull(clusters)
        assertNull(
            "a solid-color garment on a transparent background should yield no secondary color",
            clusters!!.second,
        )
    }
}
