package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import android.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BITMAP_SIZE = 32
private const val SAMPLE_PIXEL_ARGB = 0xFF112233.toInt()

/**
 * M13 Phase 3 (Privacy Validation) — a concrete regression test for the
 * "never copies source EXIF back in" guarantee
 * [com.wardrobe.app.core.ai.privacy.PrivacyPreprocessor]'s own KDoc
 * describes (ADR-012 §2): every cloud call's actual outbound bytes come
 * from [toBase64Webp], and `Bitmap.compress()` has no API surface to
 * attach EXIF metadata, so no encoded payload should ever contain a WebP
 * EXIF chunk or a JPEG EXIF marker's literal byte signature.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapEncodingTest {
    @Test
    fun `toBase64Webp never embeds an EXIF chunk or marker in the encoded bytes`() {
        val bitmap =
            Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, SAMPLE_PIXEL_ARGB)
            }

        val encodedBytes = Base64.decode(bitmap.toBase64Webp(), Base64.NO_WRAP)

        assertFalse(
            "encoded payload must never contain a WebP EXIF chunk",
            encodedBytes.containsAscii("EXIF"),
        )
        assertFalse(
            "encoded payload must never contain a JPEG EXIF APP1 marker",
            encodedBytes.containsAscii("Exif"),
        )
    }
}

private fun ByteArray.containsAscii(needle: String): Boolean {
    val pattern = needle.toByteArray(Charsets.US_ASCII)
    if (pattern.isEmpty() || size < pattern.size) return false
    for (start in 0..size - pattern.size) {
        if (pattern.indices.all { offset -> this[start + offset] == pattern[offset] }) return true
    }
    return false
}
