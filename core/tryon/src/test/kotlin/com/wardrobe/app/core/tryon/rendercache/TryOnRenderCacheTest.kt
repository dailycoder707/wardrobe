package com.wardrobe.app.core.tryon.rendercache

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TryOnRenderCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun pngFile(color: Int): File {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val file = File.createTempFile("tryon_render_cache_", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun layer(
        cutoutPath: String,
        checksum: String,
        templateUpdatedAt: Long = 100L,
        maskUpdatedAt: Long? = null,
    ) = TryOnRenderLayerInput(
        cutoutFilePath = cutoutPath,
        cutoutChecksum = checksum,
        maskFilePath = null,
        maskUpdatedAtEpochMillis = maskUpdatedAt,
        templateId = 1L,
        templateUpdatedAtEpochMillis = templateUpdatedAt,
        offsetXFraction = 0.5f,
        offsetYFraction = 0.3f,
        scale = 1f,
        rotationDegrees = 0f,
    )

    @Test
    fun `rendering twice with the same key reuses the cached bitmap, not recomputed`() {
        val cache = TryOnRenderCache()
        val background = pngFile(Color.WHITE)
        val cutout = pngFile(Color.RED)
        val key = TryOnRenderCacheKey(1000L, 2000L, listOf(layer(cutout.path, "checksum-1")))

        val first = cache.render(background.path, key)
        val second = cache.render(background.path, key)

        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `a changed cutout checksum forces a fresh render, not the cached bitmap`() {
        val cache = TryOnRenderCache()
        val background = pngFile(Color.WHITE)
        val cutout = pngFile(Color.RED)
        val original = TryOnRenderCacheKey(1000L, 2000L, listOf(layer(cutout.path, "checksum-1")))
        val recut = TryOnRenderCacheKey(1000L, 2000L, listOf(layer(cutout.path, "checksum-2")))

        val first = cache.render(background.path, original)
        val second = cache.render(background.path, recut)

        assertNotSame(first, second)
    }

    @Test
    fun `a changed body profile updatedAt forces a fresh render`() {
        val cache = TryOnRenderCache()
        val background = pngFile(Color.WHITE)
        val cutout = pngFile(Color.RED)
        val original = TryOnRenderCacheKey(1000L, 2000L, listOf(layer(cutout.path, "checksum-1")))
        val recaptured = TryOnRenderCacheKey(1500L, 2000L, listOf(layer(cutout.path, "checksum-1")))

        val first = cache.render(background.path, original)
        val second = cache.render(background.path, recaptured)

        assertNotSame(first, second)
    }

    @Test
    fun `invalidateAll forces the next render to recompute even with the same key`() {
        val cache = TryOnRenderCache()
        val background = pngFile(Color.WHITE)
        val cutout = pngFile(Color.RED)
        val key = TryOnRenderCacheKey(1000L, 2000L, listOf(layer(cutout.path, "checksum-1")))

        val first = cache.render(background.path, key)
        cache.invalidateAll()
        val second = cache.render(background.path, key)

        assertNotSame(first, second)
    }

    // A "background photo path that fails to decode returns null rather than
    // crashing" case is intentionally not covered here: Robolectric's default
    // `BitmapFactory` shadow doesn't faithfully reproduce real Android's
    // file-not-found behavior (it returns a stub bitmap rather than null),
    // so a test against a missing path here would assert something this
    // environment can't actually verify — the production `?: null` guard
    // remains in `render()`, just not unit-tested, matching this project's
    // "don't fabricate confidence" discipline (Constitution rule 4).
}
