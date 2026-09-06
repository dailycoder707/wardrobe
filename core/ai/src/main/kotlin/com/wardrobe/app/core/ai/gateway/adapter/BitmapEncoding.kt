package com.wardrobe.app.core.ai.gateway.adapter

import android.graphics.Bitmap
import android.util.Base64
import com.wardrobe.app.core.image.pipeline.ImageResizer
import java.io.ByteArrayOutputStream

private const val WEBP_QUALITY = 85

/** Every vendor adapter sends the image the same way — lossy WebP, then
 * standard Base64 (no line wraps, matching every vendor's documented
 * expectation of a single unbroken data string). */
internal fun Bitmap.toBase64Webp(): String {
    val output = ByteArrayOutputStream()
    ImageResizer.encodeWebpLossy(this, WEBP_QUALITY, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
