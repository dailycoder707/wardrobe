package com.wardrobe.app.core.tryon.rendercache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TryOnRenderCacheKeyTest {
    private fun layer(
        cutoutChecksum: String? = "checksum-1",
        maskUpdatedAtEpochMillis: Long? = null,
        templateId: Long = 1L,
        templateUpdatedAtEpochMillis: Long = 100L,
    ) = TryOnRenderLayerInput(
        cutoutFilePath = "/cutouts/garment-1.png",
        cutoutChecksum = cutoutChecksum,
        maskFilePath = null,
        maskUpdatedAtEpochMillis = maskUpdatedAtEpochMillis,
        templateId = templateId,
        templateUpdatedAtEpochMillis = templateUpdatedAtEpochMillis,
        offsetXFraction = 0.5f,
        offsetYFraction = 0.3f,
        scale = 1f,
        rotationDegrees = 0f,
    )

    private fun key(
        bodyProfileUpdatedAtEpochMillis: Long = 1000L,
        measurementsComputedAtEpochMillis: Long? = 2000L,
        layers: List<TryOnRenderLayerInput> = listOf(layer()),
    ) = TryOnRenderCacheKey(bodyProfileUpdatedAtEpochMillis, measurementsComputedAtEpochMillis, layers)

    @Test
    fun `identical inputs produce an identical digest`() {
        assertEquals(key().digest(), key().digest())
    }

    @Test
    fun `a changed garment cutout checksum changes the digest`() {
        val original = key(layers = listOf(layer(cutoutChecksum = "checksum-1")))
        val recut = key(layers = listOf(layer(cutoutChecksum = "checksum-2")))

        assertNotEquals(original.digest(), recut.digest())
    }

    @Test
    fun `a changed placement template updatedAt changes the digest`() {
        val original = key(layers = listOf(layer(templateUpdatedAtEpochMillis = 100L)))
        val dragged = key(layers = listOf(layer(templateUpdatedAtEpochMillis = 150L)))

        assertNotEquals(original.digest(), dragged.digest())
    }

    @Test
    fun `a changed garment mask updatedAt changes the digest`() {
        val unmasked = key(layers = listOf(layer(maskUpdatedAtEpochMillis = null)))
        val remasked = key(layers = listOf(layer(maskUpdatedAtEpochMillis = 500L)))

        assertNotEquals(unmasked.digest(), remasked.digest())
    }

    @Test
    fun `a changed body profile updatedAt changes the digest`() {
        val original = key(bodyProfileUpdatedAtEpochMillis = 1000L)
        val recaptured = key(bodyProfileUpdatedAtEpochMillis = 1500L)

        assertNotEquals(original.digest(), recaptured.digest())
    }

    @Test
    fun `a changed measurements computedAt changes the digest`() {
        val original = key(measurementsComputedAtEpochMillis = 2000L)
        val recomputed = key(measurementsComputedAtEpochMillis = 2500L)

        assertNotEquals(original.digest(), recomputed.digest())
    }
}
