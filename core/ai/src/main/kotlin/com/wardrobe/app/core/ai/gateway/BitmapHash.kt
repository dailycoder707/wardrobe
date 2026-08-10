package com.wardrobe.app.core.ai.gateway

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * SHA-256 of a decoded [Bitmap]'s raw pixel data — the in-memory
 * counterpart of `core:image`'s file-based `ImageHasher.sha256`, needed
 * here because Gateway calls operate on already-decoded bitmaps, not files.
 * Used only to build cache keys, never persisted as a garment's checksum
 * (that's still `ImageMetadataDao`'s file-based checksum).
 */
internal fun sha256(bitmap: Bitmap): String {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val buffer = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
    buffer.asIntBuffer().put(pixels)
    val digest = MessageDigest.getInstance("SHA-256").digest(buffer.array())
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
